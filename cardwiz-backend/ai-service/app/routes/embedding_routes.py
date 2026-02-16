from fastapi import APIRouter, HTTPException

from app.schemas.recommendation_schema import EmbeddingSyncRequest, EmbeddingSyncResponse
from app.services.embedding_service import EmbeddingService

router = APIRouter()
embedding_service = EmbeddingService()


@router.post("/sync", response_model=EmbeddingSyncResponse)
async def sync_embedding(payload: EmbeddingSyncRequest) -> EmbeddingSyncResponse:
    try:
        result = await embedding_service.sync_rule_embedding(
            rule_id=payload.ruleId,
            card_id=payload.cardId,
            content_text=payload.contentText,
        )
        return EmbeddingSyncResponse(**result)
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Embedding sync failed: {exc}") from exc
