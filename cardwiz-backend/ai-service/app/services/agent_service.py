import json
import logging
import re

from app.clients.bedrock_client import get_bedrock_runtime_client
from app.config import settings
from app.schemas.recommendation_schema import RecommendationRequest
from app.services.embedding_service import EmbeddingService
from app.tools import calculate_reward, search_card_rules

logger = logging.getLogger("uvicorn")


class AgentService:
    def __init__(self, embedding_service: EmbeddingService):
        self.embedding_service = embedding_service
        self.bedrock = get_bedrock_runtime_client()
        self.model_id = settings.AGENT_MODEL_ID
        self.max_tool_iterations = max(1, settings.AGENT_MAX_TOOL_ITERATIONS)

    @staticmethod
    def _extract_json_payload(output_text: str) -> str:
        text = (output_text or "").strip()
        fenced_match = re.search(r"```(?:json)?\s*([\s\S]*?)\s*```", text, re.IGNORECASE)
        if fenced_match:
            return fenced_match.group(1).strip()
        start = text.find("{")
        end = text.rfind("}")
        if start != -1 and end != -1 and end > start:
            return text[start:end + 1]
        return text

    def _get_tool_config(self):
        return {
            "tools": [
                {
                    "toolSpec": {
                        "name": "search_card_rules",
                        "description": (
                            "Find candidate reward rules for merchant/category from indexed cards. "
                            "Always call this first."
                        ),
                        "inputSchema": {
                            "json": {
                                "type": "object",
                                "properties": {
                                    "merchant": {"type": "string"},
                                    "category": {"type": "string"},
                                },
                                "required": ["merchant"],
                            }
                        },
                    }
                },
                {
                    "toolSpec": {
                        "name": "calculate_reward",
                        "description": (
                            "Calculate reward value in INR. Use reward_mode=PERCENTAGE with "
                            "effective_reward_percentage from search results."
                        ),
                        "inputSchema": {
                            "json": {
                                "type": "object",
                                "properties": {
                                    "spend_amount": {"type": "number"},
                                    "reward_rate": {"type": "number"},
                                    "point_value": {"type": "number"},
                                    "reward_mode": {"type": "string"},
                                },
                                "required": ["spend_amount", "reward_rate"],
                            }
                        },
                    }
                },
            ]
        }

    def should_use_agent(self, request: RecommendationRequest) -> bool:
        use_agent, _, _ = self.get_route_metadata(request)
        return use_agent

    def get_route_metadata(self, request: RecommendationRequest) -> tuple[bool, str, str]:
        if not settings.AGENT_ENABLED:
            return False, "llm_rerank", "AGENT_DISABLED"

        notes = (request.contextNotes or "").upper()
        if "[BULK_EVAL]" in notes:
            return False, "deterministic", "BULK_EVAL_MODE"

        available_count = len(request.availableCardIds or [])
        if available_count <= 1:
            return False, "deterministic", "INSUFFICIENT_CARD_CHOICES"

        spend = 0.0
        try:
            spend = float(request.transactionAmount or 0.0)
        except Exception:
            spend = 0.0

        if spend <= 0:
            return False, "deterministic", "MISSING_OR_ZERO_SPEND"

        text = f"{request.merchantName or ''} {request.category or ''} {request.contextNotes or ''}".lower()
        complex_keywords = ("optimize", "max", "maximize", "best", "which card", "should i use", "compare")
        has_complex_intent = any(keyword in text for keyword in complex_keywords)

        if spend >= settings.AGENT_COMPLEX_SPEND_THRESHOLD:
            return True, "agent", "HIGH_SPEND_COMPLEX"
        if has_complex_intent:
            return True, "agent", "COMPLEX_INTENT"
        return False, "llm_rerank", "STANDARD_RERANK"

    @staticmethod
    def should_skip_heavy_llm(request: RecommendationRequest) -> bool:
        try:
            spend = float(request.transactionAmount or 0.0)
        except Exception:
            spend = 0.0
        return len(request.availableCardIds or []) <= 1 or spend <= 0

    def _build_agent_prompt(self, request: RecommendationRequest) -> str:
        spend = float(request.transactionAmount or 0.0)
        currency = (request.currency or "INR").upper()
        return f"""
You are a credit-card optimization agent.
Use tools to compute exact reward values and then return the best card.

User context:
- user_id: {request.userId}
- merchant: {request.merchantName}
- category: {request.category or "general"}
- spend_amount: {spend}
- currency: {currency}
- available_card_ids: {request.availableCardIds}
- context_notes: {request.contextNotes or "none"}

Execution requirements:
1. Call search_card_rules first.
2. For each promising candidate, call calculate_reward with:
   - spend_amount from user context
   - reward_rate = effective_reward_percentage
   - reward_mode = "PERCENTAGE"
3. Choose the card with highest computed reward value.
4. Mention tool usage clearly in calculation_logic/reasoning.

Return STRICT JSON only:
{{
  "best_card_id": int,
  "best_card_name": "string",
  "rewards": {{
    "estimated_value": float,
    "effective_percentage": float,
    "reward_type": "string",
    "raw_points_earned": float
  }},
  "calculation_logic": "string",
  "reasoning_bullets": ["string", "string"],
  "warning": "string",
  "comparison_table": [
    {{
      "card_id": int,
      "card_name": "string",
      "effective_percentage": float,
      "estimated_value": float,
      "verdict": "string"
    }}
  ]
}}
"""

    async def solve_optimization(self, request: RecommendationRequest) -> dict | None:
        base_prompt = self._build_agent_prompt(request)
        messages = [{"role": "user", "content": [{"text": base_prompt}]}]

        try:
            for _ in range(self.max_tool_iterations):
                response = self.bedrock.converse(
                    modelId=self.model_id,
                    messages=messages,
                    toolConfig=self._get_tool_config(),
                    inferenceConfig={"temperature": 0.0},
                )
                assistant_message = response["output"]["message"]
                stop_reason = response.get("stopReason")

                if stop_reason != "tool_use":
                    text_blocks = [c.get("text") for c in assistant_message.get("content", []) if "text" in c]
                    if not text_blocks:
                        return None
                    return json.loads(self._extract_json_payload("\n".join(text_blocks)))

                tool_results = []
                for content in assistant_message.get("content", []):
                    if "toolUse" not in content:
                        continue
                    tool_use = content["toolUse"]
                    name = tool_use.get("name")
                    tool_input = tool_use.get("input", {}) or {}

                    result_json = {"error": f"Unknown tool: {name}"}
                    if name == "search_card_rules":
                        result_json = {
                            "result": await search_card_rules(
                                embedding_service=self.embedding_service,
                                merchant=tool_input.get("merchant", request.merchantName),
                                category=tool_input.get("category", request.category),
                                user_id=int(request.userId),
                                available_card_ids=[int(card_id) for card_id in request.availableCardIds or []],
                                top_k=10,
                            )
                        }
                    elif name == "calculate_reward":
                        result_json = {
                            "result": calculate_reward(
                                spend_amount=float(tool_input.get("spend_amount", request.transactionAmount or 0.0)),
                                reward_rate=float(tool_input.get("reward_rate", 0.0)),
                                point_value=float(tool_input.get("point_value", 0.25)),
                                reward_mode=tool_input.get("reward_mode", "PERCENTAGE"),
                            )
                        }

                    tool_results.append(
                        {
                            "toolResult": {
                                "toolUseId": tool_use["toolUseId"],
                                "content": [{"json": result_json}],
                            }
                        }
                    )

                messages.append({"role": "assistant", "content": assistant_message.get("content", [])})
                messages.append({"role": "user", "content": tool_results})
        except Exception as exc:
            logger.warning("Agentic optimization failed: %s", exc)
            return None

        return None
