import hashlib
import json
import logging
import re

from botocore.exceptions import ClientError
from sqlalchemy import desc, func, select

from app.clients.bedrock_client import get_bedrock_runtime_client
from app.clients.cache_client import get_cache_client
from app.config import settings
from app.db import SessionLocal
from app.models.vector_models import RewardRuleVector


logger = logging.getLogger("uvicorn")


class EmbeddingService:
    def __init__(self):
        self.bedrock = get_bedrock_runtime_client()
        self.cache = get_cache_client()
        self.primary_model_id = settings.EMBEDDING_PRIMARY_MODEL_ID
        self.fallback_model_id = settings.EMBEDDING_FALLBACK_MODEL_ID
        self.stop_words = {
            "a", "an", "and", "at", "for", "from", "in", "is", "of", "on", "or", "the", "to", "with"
        }
        self.index_version_cache_key = "embedding:index_version"

    def sanitize_text(self, text: str) -> str:
        normalized = re.sub(r"[^a-zA-Z0-9\s]", " ", text.lower())
        tokens = [token for token in normalized.split() if token and token not in self.stop_words]
        return " ".join(tokens)

    @staticmethod
    def _hash_key(prefix: str, raw: str) -> str:
        digest = hashlib.sha256(raw.encode("utf-8")).hexdigest()
        return f"{prefix}:{digest}"

    def _get_index_version(self) -> int:
        if not self.cache:
            return 0
        try:
            raw = self.cache.get(self.index_version_cache_key)
            return int(raw) if raw is not None else 0
        except Exception:
            return 0

    def _bump_index_version(self) -> None:
        if not self.cache:
            return
        try:
            self.cache.incr(self.index_version_cache_key)
        except Exception as exc:
            logger.warning("Failed bumping embedding index version: %s", exc)

    @staticmethod
    def _is_missing_reward_rule_table(exc: Exception) -> bool:
        message = str(exc).lower()
        return "reward_rule_vectors" in message and "does not exist" in message

    def _build_embedding_payload(self, model_id: str, clean_text: str, embedding_purpose: str) -> dict:
        # Nova 2 Multimodal Embeddings (current schema)
        if "nova-2-multimodal-embeddings" in model_id:
            return {
                "schemaVersion": "nova-multimodal-embed-v1",
                "taskType": "SINGLE_EMBEDDING",
                "singleEmbeddingParams": {
                    "embeddingPurpose": embedding_purpose,
                    "embeddingDimension": settings.EMBEDDING_OUTPUT_LENGTH,
                    "text": {
                        "truncationMode": settings.EMBEDDING_TEXT_TRUNCATION,
                        "value": clean_text
                    }
                }
            }

        # Titan Text Embeddings v2 fallback schema
        if "titan-embed-text-v2" in model_id:
            return {
                "inputText": clean_text,
                "dimensions": settings.EMBEDDING_OUTPUT_LENGTH,
                "normalize": True
            }

        # Generic fallback
        return {"inputText": clean_text}

    @staticmethod
    def _extract_embedding(response_body: dict):
        # Nova embeddings response
        embeddings = response_body.get("embeddings")
        if isinstance(embeddings, list) and embeddings:
            first = embeddings[0]
            if isinstance(first, dict) and isinstance(first.get("embedding"), list):
                return first["embedding"]

        # Titan response
        embedding = response_body.get("embedding")
        if isinstance(embedding, list):
            return embedding

        return None

    def _invoke_embedding_model(self, model_id: str, clean_text: str, embedding_purpose: str):
        payload = self._build_embedding_payload(model_id, clean_text, embedding_purpose)
        response = self.bedrock.invoke_model(
            body=json.dumps(payload),
            modelId=model_id,
            accept="application/json",
            contentType="application/json",
        )
        response_body = json.loads(response.get("body").read())
        embedding = self._extract_embedding(response_body)
        if embedding and len(embedding) > settings.EMBEDDING_OUTPUT_LENGTH:
            embedding = embedding[:settings.EMBEDDING_OUTPUT_LENGTH]
        return embedding

    async def get_embedding(self, text: str, embedding_purpose: str | None = None):
        clean_text = self.sanitize_text(text)
        purpose = embedding_purpose or settings.EMBEDDING_INDEX_PURPOSE
        embedding_cache_key = self._hash_key("embedding", f"{purpose}|{clean_text}")
        if self.cache:
            cached_embedding = self.cache.get(embedding_cache_key)
            if cached_embedding:
                return json.loads(cached_embedding)

        try:
            embedding = self._invoke_embedding_model(self.primary_model_id, clean_text, purpose)
        except ClientError as exc:
            logger.warning("Primary model %s failed: %s", self.primary_model_id, exc)
            if self.fallback_model_id:
                logger.info("Falling back to %s", self.fallback_model_id)
                try:
                    embedding = self._invoke_embedding_model(self.fallback_model_id, clean_text, purpose)
                except Exception as fallback_exc:
                    logger.error("Fallback model %s failed: %s", self.fallback_model_id, fallback_exc)
                    raise
            else:
                raise

        if not embedding and self.fallback_model_id and self.fallback_model_id != self.primary_model_id:
            logger.warning(
                "Primary embedding model returned empty vector. Retrying with fallback model %s.",
                self.fallback_model_id,
            )
            embedding = self._invoke_embedding_model(self.fallback_model_id, clean_text, purpose)

        if embedding and self.cache:
            try:
                self.cache.setex(embedding_cache_key, settings.CACHE_TTL_SECONDS, json.dumps(embedding))
            except Exception as exc:
                logger.warning("Failed caching embedding lookup: %s", exc)

        return embedding

    async def sync_rule_embedding(self, rule_id: int, card_id: int, content_text: str):
        embedding = await self.get_embedding(content_text, settings.EMBEDDING_INDEX_PURPOSE)
        if not embedding:
            raise ValueError("No embedding returned from Bedrock")

        with SessionLocal() as session:
            existing = session.execute(
                select(RewardRuleVector).where(RewardRuleVector.rule_id == rule_id)
            ).scalar_one_or_none()

            if existing:
                existing.card_id = card_id
                existing.content_text = content_text
                existing.embedding = embedding
            else:
                session.add(
                    RewardRuleVector(
                        rule_id=rule_id,
                        card_id=card_id,
                        content_text=content_text,
                        embedding=embedding,
                    )
                )
            session.commit()

        self._bump_index_version()
        return {"status": "SYNCED", "ruleId": rule_id}

    async def search_similar_rules(self, query_text: str, top_k: int | None = None):
        query_embedding = await self.get_embedding(query_text, settings.EMBEDDING_RETRIEVAL_PURPOSE)
        if not query_embedding:
            raise ValueError("No query embedding generated for hybrid search")

        k = top_k or settings.VECTOR_TOP_K
        normalized_query = self.sanitize_text(query_text) or "general"
        index_version = self._get_index_version()
        query_cache_key = self._hash_key(
            "embedding-lookup",
            (
                f"{normalized_query}|{k}|"
                f"{settings.HYBRID_VECTOR_WEIGHT:.4f}|{settings.HYBRID_KEYWORD_WEIGHT:.4f}|"
                f"{settings.HYBRID_FTS_LANGUAGE}|v={index_version}"
            ),
        )
        if self.cache:
            cached_rules = self.cache.get(query_cache_key)
            if cached_rules:
                return json.loads(cached_rules)

        try:
            with SessionLocal() as session:
                vector_score = (1.0 - RewardRuleVector.embedding.cosine_distance(query_embedding)).label("vector_score")
                text_query = func.plainto_tsquery(settings.HYBRID_FTS_LANGUAGE, normalized_query)
                text_score = func.ts_rank(
                    func.to_tsvector(settings.HYBRID_FTS_LANGUAGE, func.coalesce(RewardRuleVector.content_text, "")),
                    text_query,
                ).label("text_score")
                final_score = (
                    (vector_score * settings.HYBRID_VECTOR_WEIGHT)
                    + (text_score * settings.HYBRID_KEYWORD_WEIGHT)
                ).label("final_score")

                rows = session.execute(
                    select(RewardRuleVector, vector_score, text_score, final_score)
                    .order_by(desc(final_score))
                    .limit(k)
                ).all()
        except Exception as exc:
            if self._is_missing_reward_rule_table(exc):
                logger.warning(
                    "reward_rule_vectors table missing during hybrid search. "
                    "Run startup init/migrations and ingest documents. Returning empty candidates."
                )
                return []
            raise

        payload = [
            {
                "rule_id": row_data.rule_id,
                "card_id": row_data.card_id,
                "content_text": row_data.content_text,
                "vector_score": round(float(v_score or 0.0), 6),
                "text_score": round(float(t_score or 0.0), 6),
                "final_score": round(float(f_score or 0.0), 6),
            }
            for row_data, v_score, t_score, f_score in rows
        ]
        if self.cache:
            try:
                self.cache.setex(query_cache_key, settings.CACHE_TTL_SECONDS, json.dumps(payload))
            except Exception as exc:
                logger.warning("Failed caching embedding search payload: %s", exc)
        return payload

    async def get_card_rule_coverage(self, card_ids: list[int]) -> set[int]:
        if not card_ids:
            return set()

        normalized_ids = sorted({int(card_id) for card_id in card_ids if card_id is not None})
        if not normalized_ids:
            return set()

        try:
            with SessionLocal() as session:
                rows = session.execute(
                    select(RewardRuleVector.card_id)
                    .where(RewardRuleVector.card_id.in_(normalized_ids))
                    .distinct()
                ).all()
        except Exception as exc:
            if self._is_missing_reward_rule_table(exc):
                logger.warning(
                    "reward_rule_vectors table missing during coverage check. "
                    "Returning empty coverage until ingestion/bootstrap completes."
                )
                return set()
            raise

        return {int(row[0]) for row in rows if row and row[0] is not None}
