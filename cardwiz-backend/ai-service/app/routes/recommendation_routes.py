from fastapi import APIRouter, HTTPException

from app.schemas.recommendation_schema import RecommendationRequest, RecommendationResponse
from app.services.recommendation_service import RecommendationService

router = APIRouter()
recommendation_service = RecommendationService()


@router.post("/rank", response_model=RecommendationResponse)
async def recommend_card(payload: RecommendationRequest) -> RecommendationResponse:
    try:
        return await recommendation_service.get_recommendation(payload)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Recommendation failed: {exc}") from exc
