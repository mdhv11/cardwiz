from fastapi import APIRouter, HTTPException, Response

from app.config import settings
from app.schemas.recommendation_schema import (
    RecommendationRequest,
    RecommendationResponse,
    StatementMissedSavingsRequest,
    StatementMissedSavingsResponse,
)
from app.services.rate_limit_service import check_rate_limit
from app.services.recommendation_service import RecommendationService

router = APIRouter()
recommendation_service = RecommendationService()


@router.post("/rank", response_model=RecommendationResponse)
async def recommend_card(
    payload: RecommendationRequest,
    response: Response,
) -> RecommendationResponse:
    actor_key = f"user:{payload.userId}"
    decision = check_rate_limit(
        namespace="recommend-rank",
        actor_key=actor_key,
        limit=settings.AI_RATE_LIMIT_RECOMMEND_PER_WINDOW,
        window_seconds=settings.AI_RATE_LIMIT_WINDOW_SECONDS,
    )
    response.headers["X-RateLimit-Limit"] = str(decision.limit)
    response.headers["X-RateLimit-Remaining"] = str(decision.remaining)
    if not decision.allowed:
        raise HTTPException(
            status_code=429,
            detail="Too many recommendation requests. Please retry shortly.",
            headers={"Retry-After": str(decision.retry_after_seconds)},
        )
    try:
        return await recommendation_service.get_recommendation(payload)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Recommendation failed: {exc}") from exc


@router.post("/statement-missed-savings", response_model=StatementMissedSavingsResponse)
async def statement_missed_savings(
    payload: StatementMissedSavingsRequest,
    response: Response,
) -> StatementMissedSavingsResponse:
    actor_key = f"user:{payload.userId}"
    decision = check_rate_limit(
        namespace="statement-missed-savings",
        actor_key=actor_key,
        limit=settings.AI_RATE_LIMIT_STATEMENT_PER_WINDOW,
        window_seconds=settings.AI_RATE_LIMIT_WINDOW_SECONDS,
    )
    response.headers["X-RateLimit-Limit"] = str(decision.limit)
    response.headers["X-RateLimit-Remaining"] = str(decision.remaining)
    if not decision.allowed:
        raise HTTPException(
            status_code=429,
            detail="Too many statement analyses. Please retry shortly.",
            headers={"Retry-After": str(decision.retry_after_seconds)},
        )
    try:
        return await recommendation_service.analyze_statement_missed_savings(payload)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Statement analysis failed: {exc}") from exc
