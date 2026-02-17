import hashlib
import logging

import httpx

from app.config import settings
from app.services.document_service import DocumentService
from app.services.embedding_service import EmbeddingService

logger = logging.getLogger("uvicorn")


class IngestionService:
    def __init__(self):
        self.document_service = DocumentService()
        self.embedding_service = EmbeddingService()

    async def process_event(self, payload: dict) -> None:
        document_id = int(payload["documentId"])
        card_id = int(payload["cardId"])
        s3_key = payload["s3Key"]
        bucket_name = payload.get("bucketName") or settings.effective_s3_bucket

        logger.info(
            "Processing Kafka ingest event documentId=%s cardId=%s s3Key=%s",
            document_id,
            card_id,
            s3_key,
        )

        try:
            analysis = await self.document_service.analyze_document(
                s3_key=s3_key,
                bucket=bucket_name,
                doc_id=document_id,
            )
            rules = analysis.extractedRules or []
            for idx, rule in enumerate(rules):
                content_text = self._build_rule_content_text(rule)
                rule_id = self._stable_rule_id(document_id, card_id, idx, content_text)
                await self.embedding_service.sync_rule_embedding(
                    rule_id=rule_id,
                    card_id=card_id,
                    content_text=content_text,
                )

            await self._notify_callback(
                {
                    "documentId": document_id,
                    "cardId": card_id,
                    "status": "COMPLETED",
                    "aiSummary": analysis.aiSummary,
                    "error": None,
                }
            )
            logger.info(
                "Kafka ingest completed documentId=%s cardId=%s rules=%s",
                document_id,
                card_id,
                len(rules),
            )
        except Exception as exc:
            logger.error(
                "Kafka ingest failed documentId=%s cardId=%s: %s",
                document_id,
                card_id,
                exc,
            )
            await self._notify_callback(
                {
                    "documentId": document_id,
                    "cardId": card_id,
                    "status": "FAILED",
                    "aiSummary": None,
                    "error": str(exc),
                }
            )

    async def _notify_callback(self, callback_payload: dict) -> None:
        headers = {
            "X-AI-CALLBACK-SECRET": settings.USER_SERVICE_CALLBACK_SECRET,
            "Content-Type": "application/json",
        }
        try:
            async with httpx.AsyncClient(timeout=15.0) as client:
                response = await client.post(
                    settings.USER_SERVICE_CALLBACK_URL,
                    json=callback_payload,
                    headers=headers,
                )
                response.raise_for_status()
        except Exception as exc:
            logger.error("Failed sending ingestion callback: %s", exc)

    @staticmethod
    def _stable_rule_id(document_id: int, card_id: int, index: int, content_text: str) -> int:
        raw = f"{document_id}|{card_id}|{index}|{content_text}"
        # reward_rule_vectors.rule_id is PostgreSQL INTEGER (int4).
        # Keep IDs deterministic but always within signed int32 range.
        digest = hashlib.sha256(raw.encode("utf-8")).hexdigest()[:16]
        return (int(digest, 16) % 2_147_483_647) + 1

    @staticmethod
    def _round2(value: float) -> float:
        return round(float(value), 2)

    def _build_rule_content_text(self, rule) -> str:
        card_name = getattr(rule, "cardName", None) or "unknown"
        category = getattr(rule, "category", None) or "general"
        reward_type = getattr(rule, "rewardType", None)
        reward_type_str = reward_type.value if hasattr(reward_type, "value") else str(reward_type or "REWARD")
        reward_rate = getattr(rule, "rewardRate", None) or 0
        points_per_unit = getattr(rule, "pointsPerUnit", None)
        spend_unit = getattr(rule, "spendUnit", None)
        point_value_rupees = getattr(rule, "pointValueRupees", None)
        effective_pct = getattr(rule, "effectiveRewardPercentage", None)
        conditions = (getattr(rule, "conditions", None) or "none").replace(";", ",")

        if effective_pct is None:
            if reward_type_str.upper() == "CASHBACK":
                effective_pct = float(reward_rate)
            elif (
                reward_type_str.upper() == "POINTS"
                and points_per_unit is not None
                and spend_unit
                and float(spend_unit) > 0
            ):
                point_value = point_value_rupees if point_value_rupees is not None else 0.25
                effective_pct = (float(points_per_unit) * float(point_value) / float(spend_unit)) * 100.0
            else:
                effective_pct = float(reward_rate)

        return (
            f"card_name={card_name};"
            f"category={category};"
            f"reward_type={reward_type_str};"
            f"reward_rate={reward_rate};"
            f"points_per_unit={points_per_unit if points_per_unit is not None else 'null'};"
            f"spend_unit={spend_unit if spend_unit is not None else 'null'};"
            f"point_value_rupees={point_value_rupees if point_value_rupees is not None else 'null'};"
            f"effective_reward_percentage={self._round2(effective_pct)};"
            f"conditions={conditions}"
        )
