import json

from app.clients.bedrock_client import get_bedrock_runtime_client
from app.config import settings
from app.services.embedding_service import EmbeddingService
from app.schemas.recommendation_schema import (
    CardRecommendation,
    RecommendationRequest,
    RecommendationResponse,
)


class RecommendationService:
    def __init__(self):
        self.embedding_service = EmbeddingService()
        self.bedrock = get_bedrock_runtime_client()
        self.model_id = "us.amazon.nova-2-pro-v1:0"

    def _build_reasoning_kwargs(self, message: dict) -> dict:
        kwargs = {
            "modelId": self.model_id,
            "messages": [message],
            "inferenceConfig": {"temperature": 0},
        }
        if settings.NOVA_ENABLE_REASONING:
            kwargs["additionalModelRequestFields"] = {
                "reasoningConfig": {"budgetTokens": settings.NOVA_REASONING_BUDGET_TOKENS}
            }
        return kwargs

    async def _build_reasoning(self, merchant_name: str, category: str, reward_text: str) -> str:
        prompt = f"""
Explain in 1-2 lines why this card is best.
Merchant: {merchant_name}
Category: {category}
Candidate reward rule: {reward_text}
Return plain text only.
"""
        message = {"role": "user", "content": [{"text": prompt}]}
        response = self.bedrock.converse(**self._build_reasoning_kwargs(message))
        return response["output"]["message"]["content"][0]["text"].strip()

    async def get_recommendation(self, request: RecommendationRequest) -> RecommendationResponse:
        if not request.availableCardIds:
            raise ValueError("availableCardIds must contain at least one card ID")

        category = request.category or "general purchases"
        semantic_query = f"{request.merchantName} {category}"
        ranked_rules = await self.embedding_service.search_similar_rules(
            query_text=semantic_query,
            top_k=settings.VECTOR_TOP_K
        )

        eligible = set(request.availableCardIds)
        survivors = [row for row in ranked_rules if row.card_id in eligible]

        if not survivors:
            best_card_id = request.availableCardIds[0]
            best_option = CardRecommendation(
                cardId=best_card_id,
                cardName=f"Card {best_card_id}",
                estimatedReward="No indexed reward rule found",
                reasoning="No vector match found in the eligible cards. Falling back to first eligible card.",
                confidenceScore=0.35,
            )
            return RecommendationResponse(
                bestOption=best_option,
                semanticContext=f"merchant={request.merchantName}, category={category}, fallback=true",
            )

        best = survivors[0]
        best_reasoning = await self._build_reasoning(
            merchant_name=request.merchantName,
            category=category,
            reward_text=best.content_text,
        )
        best_option = CardRecommendation(
            cardId=best.card_id,
            cardName=f"Card {best.card_id}",
            estimatedReward=best.content_text,
            reasoning=best_reasoning,
            confidenceScore=0.86,
        )

        alternatives = []
        for alt in survivors[1:3]:
            alternatives.append(
                CardRecommendation(
                    cardId=alt.card_id,
                    cardName=f"Card {alt.card_id}",
                    estimatedReward=alt.content_text,
                    reasoning="Strong alternative from vector similarity ranking.",
                    confidenceScore=0.72,
                )
            )

        return RecommendationResponse(
            bestOption=best_option,
            alternatives=alternatives,
            semanticContext=json.dumps(
                {
                    "merchant": request.merchantName,
                    "category": category,
                    "topK": settings.VECTOR_TOP_K,
                    "survivorCount": len(survivors),
                }
            ),
        )
