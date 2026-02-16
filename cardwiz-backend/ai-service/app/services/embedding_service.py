import json
import re

from sqlalchemy import select

from app.clients.bedrock_client import get_bedrock_runtime_client
from app.config import settings
from app.db import SessionLocal
from app.models.vector_models import RewardRuleVector

class EmbeddingService:
    def __init__(self):
        self.bedrock = get_bedrock_runtime_client()
        self.model_id = "amazon.nova-2-multimodal-embeddings-v1:0"
        self.stop_words = {
            "a", "an", "and", "at", "for", "from", "in", "is", "of", "on", "or", "the", "to", "with"
        }

    def sanitize_text(self, text: str) -> str:
        normalized = re.sub(r"[^a-zA-Z0-9\s]", " ", text.lower())
        tokens = [token for token in normalized.split() if token and token not in self.stop_words]
        return " ".join(tokens)

    async def get_embedding(self, text: str):
        clean_text = self.sanitize_text(text)

        # The 2026 'invoke_model' schema for Nova Multimodal Embeddings
        body = json.dumps({
            "inputText": clean_text,
            "embeddingConfig": {
                "outputEmbeddingLength": 1024 # Matches our DB column size
            }
        })

        response = self.bedrock.invoke_model(
            body=body,
            modelId=self.model_id,
            accept="application/json",
            contentType="application/json"
        )

        response_body = json.loads(response.get("body").read())
        return response_body.get("embedding")

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

        with SessionLocal() as session:
            rows = session.execute(
                select(RewardRuleVector)
                .order_by(RewardRuleVector.embedding.cosine_distance(query_embedding))
                .limit(k)
            ).scalars().all()

        return rows
