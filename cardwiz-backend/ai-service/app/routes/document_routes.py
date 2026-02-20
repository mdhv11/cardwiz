from fastapi import APIRouter, HTTPException, Request, Response

from app.config import settings
from app.schemas.document_schema import AnalyzeDocumentRequest, NovaAnalysisResponse
from app.services.rate_limit_service import check_rate_limit
from app.services.document_service import DocumentService

router = APIRouter()
document_service = DocumentService()


@router.post("/analyze", response_model=NovaAnalysisResponse)
async def analyze_document(
    payload: AnalyzeDocumentRequest,
    request: Request,
    response: Response,
) -> NovaAnalysisResponse:
    client_ip = request.client.host if request.client and request.client.host else "unknown"
    actor_key = f"doc:{payload.docId}:ip:{client_ip}"
    decision = check_rate_limit(
        namespace="documents-analyze",
        actor_key=actor_key,
        limit=settings.AI_RATE_LIMIT_DOCUMENT_PER_WINDOW,
        window_seconds=settings.AI_RATE_LIMIT_WINDOW_SECONDS,
    )
    response.headers["X-RateLimit-Limit"] = str(decision.limit)
    response.headers["X-RateLimit-Remaining"] = str(decision.remaining)
    if not decision.allowed:
        raise HTTPException(
            status_code=429,
            detail="Too many document analysis requests. Please retry shortly.",
            headers={"Retry-After": str(decision.retry_after_seconds)},
        )
    try:
        return await document_service.analyze_document(
            s3_key=payload.s3Key,
            bucket=payload.bucket,
            doc_id=payload.docId,
        )
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Document analysis failed: {exc}") from exc
