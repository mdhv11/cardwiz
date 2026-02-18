import json
import logging
import re
import uuid

from app.clients.bedrock_client import get_bedrock_runtime_client
from app.services.agent_service import AgentService
from app.services.document_service import DocumentService
from app.services.embedding_service import EmbeddingService
from app.schemas.recommendation_schema import (
    BestCardDetails,
    CardRecommendation,
    ComparisonCard,
    RecommendationRequest,
    RecommendationResponse,
    RecommendationRewards,
    StatementMissedSavingsRequest,
    StatementMissedSavingsResponse,
    StatementMissedSavingsSummary,
    StatementTransactionReport,
    TransactionContext,
)

logger = logging.getLogger("uvicorn")


class RecommendationService:
    def __init__(self):
        self.embedding_service = EmbeddingService()
        self.agent_service = AgentService(self.embedding_service)
        self.document_service = DocumentService()
        self.bedrock = get_bedrock_runtime_client()
        self.model_id = "us.amazon.nova-lite-v1:0"

    def _build_math_aware_prompt(
        self,
        merchant: str,
        category: str,
        amount: float,
        currency: str,
        context_notes: str,
        candidates: list[dict],
    ) -> str:
        candidates_text = "\n".join([
            f"- Card {c['card_id']}: {c.get('content_text', '')}" for c in candidates
        ])

        return f"""
You are a precise financial calculator for credit-card rewards.
User scenario:
- Merchant: {merchant}
- Category: {category}
- Spend Amount: {amount}
- Currency: {currency}
- Extra Context: {context_notes or 'none'}

Analyze these card rules and calculate expected reward value and effective percentage:
{candidates_text}

Guidelines:
1. If a rule gives points and point value is missing, assume 1 point = 0.25 INR equivalent.
2. For cashback, use the exact percentage.
3. Effective Percentage = (Reward Value / Spend Amount) * 100.
4. Prefer rules tied to the merchant/category context.
5. Provide concise, decision-oriented reasoning.

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

    async def _get_llm_decision(self, prompt: str) -> dict | None:
        try:
            response = self.bedrock.converse(
                modelId=self.model_id,
                messages=[{"role": "user", "content": [{"text": prompt}]}],
                inferenceConfig={"temperature": 0.0},
            )
            response_text = response["output"]["message"]["content"][0]["text"]
            json_str = response_text.replace("```json", "").replace("```", "").strip()
            return json.loads(json_str)
        except Exception as exc:
            logger.error("LLM rerank failed: %s", exc)
            return None

    async def get_recommendation(self, request: RecommendationRequest) -> RecommendationResponse:
        routing_mode = "llm_rerank"
        routing_reason = "UNSET"
        requested_ids = [int(card_id) for card_id in request.availableCardIds or []]
        covered_ids = sorted(await self.embedding_service.get_card_rule_coverage(requested_ids))
        missing_ids = sorted(set(requested_ids) - set(covered_ids))
        has_sufficient_data = len(covered_ids) >= 2 if len(requested_ids) >= 2 else len(covered_ids) >= 1

        if not request.availableCardIds:
            routing_mode = "none"
            routing_reason = "NO_AVAILABLE_CARDS"
            empty_best = CardRecommendation(
                cardId=-1,
                cardName="Unknown",
                estimatedReward="N/A",
                reasoning="No cards available to analyze.",
                confidenceScore=0.0,
            )
            spend = self._normalized_spend(request.transactionAmount)
            currency = (request.currency or "INR").upper()
            return RecommendationResponse(
                bestOption=empty_best,
                recommendation_id=f"rec_{uuid.uuid4().hex[:10]}",
                transaction_context=TransactionContext(
                    merchant=request.merchantName,
                    category=request.category or "general",
                    spend_amount=spend,
                    currency=currency,
                ),
                comparison_table=[],
                alternatives=[],
                semanticContext="No eligible cards found.",
                covered_card_ids=covered_ids,
                missing_card_ids=missing_ids,
                has_sufficient_data=False,
                routing_mode=routing_mode,
                routing_reason=routing_reason,
            )

        category = request.category or "general"
        currency = (request.currency or "INR").upper()
        spend = self._normalized_spend(request.transactionAmount)
        context_notes = request.contextNotes or ""

        query_text = f"{request.merchantName} {category} {currency} {context_notes}"
        raw_candidates = await self.embedding_service.search_similar_rules(query_text=query_text, top_k=8)

        eligible_ids = set(request.availableCardIds)
        survivors = [c for c in raw_candidates if c["card_id"] in eligible_ids]

        if not survivors:
            routing_mode = "none"
            routing_reason = "NO_SURVIVING_RULES"
            fallback_id = request.availableCardIds[0]
            fallback_best = CardRecommendation(
                cardId=fallback_id,
                cardName=f"Card {fallback_id}",
                estimatedReward="Unknown",
                reasoning="No relevant reward rules found for this merchant/category.",
                confidenceScore=0.1,
            )
            return RecommendationResponse(
                bestOption=fallback_best,
                recommendation_id=f"rec_{uuid.uuid4().hex[:10]}",
                transaction_context=TransactionContext(
                    merchant=request.merchantName,
                    category=category,
                    spend_amount=spend,
                    currency=currency,
                ),
                semanticContext="No matching embedded rules for eligible cards.",
                covered_card_ids=covered_ids,
                missing_card_ids=missing_ids,
                has_sufficient_data=has_sufficient_data,
                routing_mode=routing_mode,
                routing_reason=routing_reason,
            )

        decision = None
        should_use_agent, initial_mode, initial_reason = self.agent_service.get_route_metadata(request)
        routing_mode = initial_mode
        routing_reason = initial_reason

        if should_use_agent:
            decision = await self.agent_service.solve_optimization(request)
            if decision:
                routing_mode = "agent"
                routing_reason = f"{initial_reason}:AGENT_SUCCESS"
            else:
                routing_reason = f"{initial_reason}:AGENT_NO_DECISION"

        if not decision and routing_mode == "deterministic":
            decision = self._deterministic_decision(survivors, spend)
            routing_reason = f"{routing_reason}:DETERMINISTIC_FASTPATH"
        elif not decision:
            prompt = self._build_math_aware_prompt(
                merchant=request.merchantName,
                category=category,
                amount=spend,
                currency=currency,
                context_notes=context_notes,
                candidates=survivors[:5],
            )
            decision = await self._get_llm_decision(prompt)
            if decision:
                routing_mode = "llm_rerank"
                routing_reason = f"{routing_reason}:NOVA_LITE_SUCCESS"

        if not decision:
            decision = self._deterministic_decision(survivors, spend)
            routing_mode = "deterministic"
            routing_reason = f"{routing_reason}:FINAL_DETERMINISTIC_FALLBACK"

        winner_card_id = self._pick_winner_card_id(decision, survivors)
        winner = next((s for s in survivors if s["card_id"] == winner_card_id), survivors[0])
        winner_metrics = self._extract_rule_metrics(winner.get("content_text", ""))

        best_card_name = (
            decision.get("best_card_name")
            or decision.get("card_name")
            or self._extract_card_name(winner.get("content_text", ""))
            or f"Card {winner_card_id}"
        )
        reward_type = (
            decision.get("rewards", {}).get("reward_type")
            or winner_metrics.get("reward_type")
            or "UNKNOWN"
        )
        effective_pct = self._coerce_float(
            decision.get("rewards", {}).get("effective_percentage")
        )
        if effective_pct is None:
            effective_pct = winner_metrics.get("effective_reward_percentage") or 0.0

        estimated_value = self._coerce_float(decision.get("rewards", {}).get("estimated_value"))
        if estimated_value is None:
            estimated_value = round(spend * (effective_pct / 100.0), 2)

        raw_points_earned = self._coerce_float(decision.get("rewards", {}).get("raw_points_earned"))
        if raw_points_earned is None:
            raw_points_earned = self._derive_points_earned(winner_metrics, spend)

        calculation_logic = decision.get("calculation_logic") or self._build_calculation_logic(
            winner_metrics, spend, estimated_value, currency
        )
        reasoning_bullets = self._normalize_reasoning_bullets(
            decision.get("reasoning_bullets"),
            fallback_reasoning=self._fallback_reasoning(best_card_name, effective_pct),
        )

        warning = decision.get("warning") or self._extract_warning(winner.get("content_text", ""))

        comparison_table = self._build_comparison_table(
            llm_table=decision.get("comparison_table"),
            survivors=survivors,
            winner_card_id=winner_card_id,
            spend=spend,
        )

        best_card = BestCardDetails(
            id=winner_card_id,
            name=best_card_name,
            status="WINNER",
            rewards=RecommendationRewards(
                estimated_value=round(estimated_value, 2),
                value_unit=currency,
                effective_percentage=round(effective_pct, 2),
                reward_type=str(reward_type).upper(),
                raw_points_earned=raw_points_earned,
            ),
            calculation_logic=calculation_logic,
            reasoning=reasoning_bullets,
            warning=warning,
        )

        legacy_best = CardRecommendation(
            cardId=winner_card_id,
            cardName=best_card_name,
            estimatedReward=(
                f"For {currency} {spend:,.0f} spent, you would earn: "
                f"{currency} {estimated_value:,.2f} worth of rewards ({effective_pct:.2f}%)."
            ),
            reasoning=" ".join(reasoning_bullets),
            confidenceScore=0.85,
        )

        alternatives = [
            CardRecommendation(
                cardId=item.card_id if item.card_id is not None else -1,
                cardName=item.card_name,
                estimatedReward=(
                    f"For {currency} {spend:,.0f} spent, you would earn: "
                    f"{currency} {item.estimated_value:,.2f} ({item.effective_percentage:.2f}%)."
                ),
                reasoning=item.verdict,
                confidenceScore=0.6,
            )
            for item in comparison_table[:3]
        ]

        logger.info(
            "Recommendation routing mode=%s reason=%s userId=%s merchant=%s cards=%s",
            routing_mode,
            routing_reason,
            request.userId,
            request.merchantName,
            len(request.availableCardIds or []),
        )

        return RecommendationResponse(
            recommendation_id=f"rec_{uuid.uuid4().hex[:10]}",
            transaction_context=TransactionContext(
                merchant=request.merchantName,
                category=category,
                spend_amount=spend,
                currency=currency,
            ),
            best_card=best_card,
            comparison_table=comparison_table,
            bestOption=legacy_best,
            alternatives=alternatives,
            semanticContext=(
                f"Routing={routing_mode}; reason={routing_reason}. "
                f"Analyzed {len(survivors)} rules."
            ),
            covered_card_ids=covered_ids,
            missing_card_ids=missing_ids,
            has_sufficient_data=has_sufficient_data,
            routing_mode=routing_mode,
            routing_reason=routing_reason,
        )

    async def analyze_statement_missed_savings(
        self,
        request: StatementMissedSavingsRequest,
    ) -> StatementMissedSavingsResponse:
        card_pool = list(dict.fromkeys([int(card_id) for card_id in request.availableCardIds or []]))
        actual_card_id = int(request.actualCardId)
        if actual_card_id not in card_pool:
            card_pool.append(actual_card_id)

        extracted = await self.document_service.extract_statement_transactions(
            s3_key=request.statementS3Key,
            bucket=request.bucket,
            limit=request.limitTransactions,
        )

        currency = (request.currency or "INR").upper()
        reports: list[StatementTransactionReport] = []
        total_spend = 0.0
        total_actual_rewards = 0.0
        total_optimal_rewards = 0.0

        for tx in extracted.transactions:
            amount = self._normalized_spend(tx.amount)
            total_spend += amount

            actual_result = await self.get_recommendation(
                RecommendationRequest(
                    userId=request.userId,
                    merchantName=tx.merchant,
                    category=None,
                    transactionAmount=amount,
                    currency=currency,
                    contextNotes=(request.contextNotes or "") + " [BULK_EVAL]",
                    availableCardIds=[actual_card_id],
                )
            )
            optimal_result = await self.get_recommendation(
                RecommendationRequest(
                    userId=request.userId,
                    merchantName=tx.merchant,
                    category=None,
                    transactionAmount=amount,
                    currency=currency,
                    contextNotes=(request.contextNotes or "") + " [BULK_EVAL]",
                    availableCardIds=card_pool,
                )
            )

            actual_name, actual_value = self._extract_card_value(actual_result, actual_card_id)
            optimal_id, optimal_name, optimal_value = self._extract_best_card(optimal_result)
            missed_value = max(0.0, optimal_value - actual_value)

            total_actual_rewards += actual_value
            total_optimal_rewards += optimal_value

            reports.append(
                StatementTransactionReport(
                    date=tx.date,
                    merchant=tx.merchant,
                    amount=round(amount, 2),
                    actual_card_id=actual_card_id,
                    actual_card_name=actual_name,
                    actual_reward_value=round(actual_value, 2),
                    actual_reward_source="ENGINE_ESTIMATE",
                    optimal_card_id=optimal_id,
                    optimal_card_name=optimal_name,
                    optimal_reward_value=round(optimal_value, 2),
                    missed_value=round(missed_value, 2),
                )
            )

        return StatementMissedSavingsResponse(
            statement_s3_key=request.statementS3Key,
            summary=StatementMissedSavingsSummary(
                transactions_analyzed=len(reports),
                total_spend=round(total_spend, 2),
                total_actual_rewards=round(total_actual_rewards, 2),
                total_optimal_rewards=round(total_optimal_rewards, 2),
                total_missed_savings=round(max(0.0, total_optimal_rewards - total_actual_rewards), 2),
                currency=currency,
            ),
            transactions=reports,
        )

    def _deterministic_decision(self, survivors: list[dict], spend: float) -> dict:
        scored = []
        for s in survivors:
            metrics = self._extract_rule_metrics(s.get("content_text", ""))
            pct = metrics.get("effective_reward_percentage") or 0.0
            scored.append((pct, s, metrics))
        scored.sort(key=lambda x: x[0], reverse=True)
        best_pct, best_item, best_metrics = scored[0]

        return {
            "best_card_id": best_item["card_id"],
            "best_card_name": self._extract_card_name(best_item.get("content_text", "")) or f"Card {best_item['card_id']}",
            "rewards": {
                "estimated_value": round(spend * (best_pct / 100.0), 2),
                "effective_percentage": round(best_pct, 2),
                "reward_type": best_metrics.get("reward_type") or "UNKNOWN",
                "raw_points_earned": self._derive_points_earned(best_metrics, spend),
            },
            "calculation_logic": self._build_calculation_logic(
                best_metrics,
                spend,
                round(spend * (best_pct / 100.0), 2),
                "INR",
            ),
            "reasoning_bullets": [
                "Selected based on highest normalized effective reward percentage.",
                "Ranking uses indexed card-rule math with deterministic fallback.",
            ],
            "warning": self._extract_warning(best_item.get("content_text", "")),
        }

    def _pick_winner_card_id(self, decision: dict, survivors: list[dict]) -> int:
        candidate_id = decision.get("best_card_id")
        try:
            candidate_id = int(candidate_id)
        except Exception:
            candidate_id = survivors[0]["card_id"]

        survivor_ids = {s["card_id"] for s in survivors}
        if candidate_id not in survivor_ids:
            return survivors[0]["card_id"]
        return candidate_id

    def _build_comparison_table(
        self,
        llm_table,
        survivors: list[dict],
        winner_card_id: int,
        spend: float,
    ) -> list[ComparisonCard]:
        rows: list[ComparisonCard] = []

        if isinstance(llm_table, list):
            for row in llm_table:
                if not isinstance(row, dict):
                    continue
                card_id = self._safe_int(row.get("card_id"), None)
                if card_id == winner_card_id:
                    continue
                eff = self._coerce_float(row.get("effective_percentage"))
                est = self._coerce_float(row.get("estimated_value"))
                if eff is None and est is not None and spend > 0:
                    eff = (est / spend) * 100.0
                if est is None and eff is not None:
                    est = spend * (eff / 100.0)
                if eff is None:
                    continue
                rows.append(
                    ComparisonCard(
                        card_id=card_id,
                        card_name=row.get("card_name") or f"Card {card_id if card_id is not None else '?'}",
                        effective_percentage=round(eff, 2),
                        estimated_value=round(est or 0.0, 2),
                        verdict=row.get("verdict") or "Alternative option.",
                    )
                )

        if rows:
            return rows[:3]

        for s in survivors:
            if s["card_id"] == winner_card_id:
                continue
            metrics = self._extract_rule_metrics(s.get("content_text", ""))
            pct = metrics.get("effective_reward_percentage") or 0.0
            rows.append(
                ComparisonCard(
                    card_id=s["card_id"],
                    card_name=self._extract_card_name(s.get("content_text", "")) or f"Card {s['card_id']}",
                    effective_percentage=round(pct, 2),
                    estimated_value=round(spend * (pct / 100.0), 2),
                    verdict="Lower effective reward rate for this scenario.",
                )
            )

        rows.sort(key=lambda r: r.effective_percentage, reverse=True)
        return rows[:3]

    @staticmethod
    def _normalize_reasoning_bullets(raw_bullets, fallback_reasoning: list[str]) -> list[str]:
        if isinstance(raw_bullets, list):
            cleaned = [str(item).strip() for item in raw_bullets if str(item).strip()]
            if cleaned:
                return cleaned[:3]
        return fallback_reasoning

    @staticmethod
    def _fallback_reasoning(card_name: str, effective_pct: float) -> list[str]:
        return [
            f"{card_name} provides the highest normalized return for this spend context.",
            f"Estimated effective reward is {effective_pct:.2f}% after rule normalization.",
        ]

    @staticmethod
    def _normalized_spend(transaction_amount: float | None) -> float:
        if transaction_amount is None:
            return 10000.0
        try:
            value = float(transaction_amount)
            return value if value > 0 else 10000.0
        except Exception:
            return 10000.0

    @staticmethod
    def _safe_int(value, default):
        try:
            return int(value)
        except Exception:
            return default

    @staticmethod
    def _coerce_float(value):
        try:
            if value is None:
                return None
            return float(value)
        except Exception:
            return None

    def _extract_rule_metrics(self, content_text: str) -> dict:
        metrics = {
            "effective_reward_percentage": None,
            "reward_type": None,
            "reward_rate": None,
            "points_per_unit": None,
            "spend_unit": None,
            "point_value_rupees": None,
        }
        if not content_text:
            return metrics

        def extract(pattern: str):
            match = re.search(pattern, content_text, re.IGNORECASE)
            return match.group(1).strip() if match else None

        metrics["effective_reward_percentage"] = self._coerce_float(
            extract(r"effective_reward_percentage\s*=\s*([0-9]+(?:\.[0-9]+)?)")
        )
        metrics["reward_type"] = extract(r"reward_type\s*=\s*([A-Za-z_]+)")
        metrics["reward_rate"] = self._coerce_float(extract(r"reward_rate\s*=\s*([0-9]+(?:\.[0-9]+)?)"))
        metrics["points_per_unit"] = self._coerce_float(extract(r"points_per_unit\s*=\s*([0-9]+(?:\.[0-9]+)?)"))
        metrics["spend_unit"] = self._coerce_float(extract(r"spend_unit\s*=\s*([0-9]+(?:\.[0-9]+)?)"))
        metrics["point_value_rupees"] = self._coerce_float(extract(r"point_value_rupees\s*=\s*([0-9]+(?:\.[0-9]+)?)"))

        if metrics["effective_reward_percentage"] is None:
            reward_type = (metrics["reward_type"] or "").upper()
            if reward_type == "CASHBACK" and metrics["reward_rate"] is not None:
                metrics["effective_reward_percentage"] = metrics["reward_rate"]
            elif reward_type == "POINTS":
                ppu = metrics["points_per_unit"]
                spend = metrics["spend_unit"]
                point_value = metrics["point_value_rupees"] if metrics["point_value_rupees"] is not None else 0.25
                if ppu is not None and spend and spend > 0:
                    metrics["effective_reward_percentage"] = (ppu * point_value / spend) * 100.0

        if metrics["effective_reward_percentage"] is None:
            legacy_match = re.search(r"\b([0-9]+(?:\.[0-9]+)?)\b", content_text)
            if legacy_match:
                metrics["effective_reward_percentage"] = self._coerce_float(legacy_match.group(1))

        return metrics

    def _derive_points_earned(self, metrics: dict, spend: float) -> float | None:
        reward_type = (metrics.get("reward_type") or "").upper()
        if reward_type != "POINTS":
            return None

        points_per_unit = metrics.get("points_per_unit")
        spend_unit = metrics.get("spend_unit")
        if points_per_unit is None or spend_unit is None or spend_unit <= 0:
            return None

        return round((spend / spend_unit) * points_per_unit, 2)

    def _build_calculation_logic(self, metrics: dict, spend: float, estimated_value: float, currency: str) -> str:
        reward_type = (metrics.get("reward_type") or "").upper()
        effective_pct = metrics.get("effective_reward_percentage") or 0.0

        if reward_type == "POINTS":
            points_per_unit = metrics.get("points_per_unit")
            spend_unit = metrics.get("spend_unit")
            point_value = metrics.get("point_value_rupees") if metrics.get("point_value_rupees") is not None else 0.25
            if points_per_unit and spend_unit:
                points = (spend / spend_unit) * points_per_unit
                return (
                    f"{points_per_unit:g} points per {spend_unit:g} spent. "
                    f"({spend:,.0f}/{spend_unit:g})*{points_per_unit:g} = {points:,.2f} points. "
                    f"Value @ {point_value:g} per point = {currency} {estimated_value:,.2f}."
                )

        if reward_type == "CASHBACK":
            return (
                f"Cashback {effective_pct:.2f}% on {currency} {spend:,.0f}. "
                f"Estimated value = {currency} {estimated_value:,.2f}."
            )

        return (
            f"Estimated value computed using normalized effective reward percentage "
            f"({effective_pct:.2f}%) on {currency} {spend:,.0f}."
        )

    @staticmethod
    def _extract_card_name(content_text: str) -> str | None:
        if not content_text:
            return None
        match = re.search(r"card_name\s*=\s*([^;]+)", content_text, re.IGNORECASE)
        if match:
            value = match.group(1).strip()
            return value if value and value.lower() != "unknown" else None
        return None

    @staticmethod
    def _extract_warning(content_text: str) -> str | None:
        if not content_text:
            return None
        lowered = content_text.lower()
        if "expire" in lowered:
            return "Rewards may expire; please verify validity window in card terms."
        if "cap" in lowered or "max" in lowered:
            return "Reward caps may limit actual earnings."
        return None

    @staticmethod
    def _extract_best_card(response: RecommendationResponse) -> tuple[int, str, float]:
        if response.best_card:
            return (
                int(response.best_card.id),
                response.best_card.name,
                float(response.best_card.rewards.estimated_value),
            )
        return (
            int(response.bestOption.cardId),
            response.bestOption.cardName,
            RecommendationService._parse_reward_value(response.bestOption.estimatedReward),
        )

    @staticmethod
    def _extract_card_value(response: RecommendationResponse, fallback_card_id: int) -> tuple[str, float]:
        if response.best_card:
            return response.best_card.name, float(response.best_card.rewards.estimated_value)
        return response.bestOption.cardName or f"Card {fallback_card_id}", RecommendationService._parse_reward_value(
            response.bestOption.estimatedReward
        )

    @staticmethod
    def _parse_reward_value(text: str) -> float:
        if not text:
            return 0.0
        match = re.search(r"earn:\s*[A-Za-z]{3}\s*([0-9]+(?:,[0-9]{3})*(?:\.[0-9]+)?)", text, re.IGNORECASE)
        if not match:
            match = re.search(r"\b([0-9]+(?:,[0-9]{3})*(?:\.[0-9]+)?)\b", text)
        if not match:
            return 0.0
        return float(match.group(1).replace(",", ""))
