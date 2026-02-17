# import hashlib
# import json
# import logging
# import re

# from botocore.exceptions import ClientError
# from sqlalchemy import select

# from app.clients.bedrock_client import get_bedrock_runtime_client
# from app.clients.cache_client import get_cache_client
# from app.config import settings
# from app.db import SessionLocal
# from app.models.vector_models import RewardRuleVector


# logger = logging.getLogger("uvicorn")


# class EmbeddingService:
#     def __init__(self):
#         self.bedrock = get_bedrock_runtime_client()
#         self.cache = get_cache_client()
#         self.primary_model_id = settings.EMBEDDING_PRIMARY_MODEL_ID
#         self.fallback_model_id = settings.EMBEDDING_FALLBACK_MODEL_ID
#         self.stop_words = {
#             "a", "an", "and", "at", "for", "from", "in", "is", "of", "on", "or", "the", "to", "with"
#         }

#     def sanitize_text(self, text: str) -> str:
#         normalized = re.sub(r"[^a-zA-Z0-9\s]", " ", text.lower())
#         tokens = [token for token in normalized.split() if token and token not in self.stop_words]
#         return " ".join(tokens)

#     @staticmethod
#     def _hash_key(prefix: str, raw: str) -> str:
#         digest = hashlib.sha256(raw.encode("utf-8")).hexdigest()
#         return f"{prefix}:{digest}"

#     def _embedding_request_body(self, model_id: str, clean_text: str) -> dict:
#         # Titan Text Embeddings model expects only inputText.
#         if model_id.startswith("amazon.titan-embed-text"):
#             return {"inputText": clean_text}

#         # Nova embeddings body (preferred when accepted by runtime).
#         return {
#             "inputText": clean_text,
#             "embeddingConfig": {
#                 "outputEmbeddingLength": settings.EMBEDDING_OUTPUT_LENGTH
#             }
#         }

#     def _invoke_embedding(self, model_id: str, clean_text: str):
#         response = self.bedrock.invoke_model(
#             body=json.dumps(self._embedding_request_body(model_id, clean_text)),
#             modelId=model_id,
#             accept="application/json",
#             contentType="application/json"
#         )
#         response_body = json.loads(response.get("body").read())
#         return response_body.get("embedding")

#     async def get_embedding(self, text: str):
#         clean_text = self.sanitize_text(text)
#         embedding_cache_key = self._hash_key("embedding", clean_text)
#         if self.cache:
#             cached_embedding = self.cache.get(embedding_cache_key)
#             if cached_embedding:
#                 return json.loads(cached_embedding)

#         try:
#             embedding = self._invoke_embedding(self.primary_model_id, clean_text)
#         except ClientError as exc:
#             error_message = str(exc)
#             if "required key [messages] not found" in error_message and self.fallback_model_id:
#                 logger.warning(
#                     "Primary embedding model rejected request schema. Falling back to %s.",
#                     self.fallback_model_id,
#                 )
#                 embedding = self._invoke_embedding(self.fallback_model_id, clean_text)
#             else:
#                 raise

#         if not embedding and self.fallback_model_id and self.fallback_model_id != self.primary_model_id:
#             logger.warning(
#                 "Primary embedding model returned empty vector. Retrying with fallback model %s.",
#                 self.fallback_model_id,
#             )
#             embedding = self._invoke_embedding(self.fallback_model_id, clean_text)

#         if embedding and self.cache:
#             try:
#                 self.cache.setex(embedding_cache_key, settings.CACHE_TTL_SECONDS, json.dumps(embedding))
#             except Exception as exc:
#                 logger.warning("Failed caching embedding lookup: %s", exc)
#         return embedding

#     async def sync_rule_embedding(self, rule_id: int, card_id: int, content_text: str):
#         embedding = await self.get_embedding(content_text)
#         if not embedding:
#             raise ValueError("No embedding returned from Bedrock")

#         with SessionLocal() as session:
#             existing = session.execute(
#                 select(RewardRuleVector).where(RewardRuleVector.rule_id == rule_id)
#             ).scalar_one_or_none()

#             if existing:
#                 existing.card_id = card_id
#                 existing.content_text = content_text
#                 existing.embedding = embedding
#             else:
#                 session.add(
#                     RewardRuleVector(
#                         rule_id=rule_id,
#                         card_id=card_id,
#                         content_text=content_text,
#                         embedding=embedding,
#                     )
#                 )
#             session.commit()

#         return {"status": "SYNCED", "ruleId": rule_id}

#     async def search_similar_rules(self, query_text: str, top_k: int | None = None):
#         query_embedding = await self.get_embedding(query_text)
#         k = top_k or settings.VECTOR_TOP_K
#         query_cache_key = self._hash_key("embedding-lookup", f"{self.sanitize_text(query_text)}|{k}")
#         if self.cache:
#             cached_rules = self.cache.get(query_cache_key)
#             if cached_rules:
#                 return json.loads(cached_rules)

#         with SessionLocal() as session:
#             rows = session.execute(
#                 select(RewardRuleVector)
#                 .order_by(RewardRuleVector.embedding.cosine_distance(query_embedding))
#                 .limit(k)
#             ).scalars().all()

#         payload = [
#             {
#                 "rule_id": row.rule_id,
#                 "card_id": row.card_id,
#                 "content_text": row.content_text,
#             }
#             for row in rows
#         ]
#         if self.cache:
#             try:
#                 self.cache.setex(query_cache_key, settings.CACHE_TTL_SECONDS, json.dumps(payload))
#             except Exception as exc:
#                 logger.warning("Failed caching embedding search payload: %s", exc)
#         return payload

import hashlib
import json
import logging
import re

from botocore.exceptions import ClientError
from sqlalchemy import select

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
        self.model_id = settings.EMBEDDING_PRIMARY_MODEL_ID
        self.stop_words = {
            "a", "an", "and", "at", "for", "from", "in", "is", "of", "on", "or", "the", "to", "with"
        }

    def sanitize_text(self, text: str) -> str:
        normalized = re.sub(r"[^a-zA-Z0-9\s]", " ", text.lower())
        tokens = [token for token in normalized.split() if token and token not in self.stop_words]
        return " ".join(tokens)

    @staticmethod
    def _hash_key(prefix: str, raw: str) -> str:
        digest = hashlib.sha256(raw.encode("utf-8")).hexdigest()
        return f"{prefix}:{digest}"

    def _get_embedding_payload(self, text: str, model_id: str) -> dict:
        """
        Constructs the correct payload based on the Model Family.
        """
        # Case A: Amazon Nova 2 Multimodal (Your Primary)
        if "nova-2-multimodal" in model_id:
            return {
                "inputText": text,
                "embeddingConfig": {
                    "outputEmbeddingLength": settings.EMBEDDING_OUTPUT_LENGTH  # 1024
                }
            }

        # Case B: Amazon Titan v2 (Your Fallback)
        if "titan-embed-text-v2" in model_id:
            return {
                "inputText": text,
                "dimensions": settings.EMBEDDING_OUTPUT_LENGTH, # 1024
                "normalize": True
            }
        
        # Default/Generic Fallback
        return {"inputText": text}

    async def get_embedding(self, text: str):
        clean_text = self.sanitize_text(text)
        embedding_cache_key = self._hash_key("embedding", clean_text)
        
        if self.cache:
            cached = self.cache.get(embedding_cache_key)
            if cached:
                return json.loads(cached)

        # Try Primary Model (Nova 2)
        try:
            return await self._invoke_bedrock(self.model_id, clean_text, embedding_cache_key)
        except ClientError as e:
            logger.warning(f"Primary model {self.model_id} failed: {e}")
            
            # Try Fallback Model (Titan) if configured
            if settings.EMBEDDING_FALLBACK_MODEL_ID:
                logger.info(f"Falling back to {settings.EMBEDDING_FALLBACK_MODEL_ID}")
                return await self._invoke_bedrock(settings.EMBEDDING_FALLBACK_MODEL_ID, clean_text, embedding_cache_key)
            raise e

    async def _invoke_bedrock(self, model_id: str, text: str, cache_key: str):
        body = self._get_embedding_payload(text, model_id)
        
        response = self.bedrock.invoke_model(
            body=json.dumps(body),
            modelId=model_id,
            accept="application/json",
            contentType="application/json"
        )

        response_body = json.loads(response.get("body").read())
        embedding = response_body.get("embedding")

        # Validation: Check dimensions
        if embedding and len(embedding) != settings.EMBEDDING_OUTPUT_LENGTH:
            # Nova/Titan usually handle this, but good to double check
            embedding = embedding[:settings.EMBEDDING_OUTPUT_LENGTH]

        if embedding and self.cache:
            self.cache.setex(cache_key, settings.CACHE_TTL_SECONDS, json.dumps(embedding))

        return embedding

    async def sync_rule_embedding(self, rule_id: int, card_id: int, content_text: str):
        embedding = await self.get_embedding(content_text)
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

        return {"status": "SYNCED", "ruleId": rule_id}

    async def search_similar_rules(self, query_text: str, top_k: int | None = None):
        query_embedding = await self.get_embedding(query_text)
        k = top_k or settings.VECTOR_TOP_K

        search_cache_key = self._hash_key("search", f"{query_text}|{k}")
        if self.cache:
            cached_results = self.cache.get(search_cache_key)
            if cached_results:
                return json.loads(cached_results)

        with SessionLocal() as session:
            rows = session.execute(
                select(RewardRuleVector)
                .order_by(RewardRuleVector.embedding.cosine_distance(query_embedding))
                .limit(k)
            ).scalars().all()

        payload = [
            {
                "rule_id": row.rule_id,
                "card_id": row.card_id,
                "content_text": row.content_text,
            }
            for row in rows
        ]

        if self.cache:
            self.cache.setex(search_cache_key, settings.CACHE_TTL_SECONDS, json.dumps(payload))

        return payload