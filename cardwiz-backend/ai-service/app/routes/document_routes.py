from fastapi import APIRouter, HTTPException

from app.schemas.document_schema import AnalyzeDocumentRequest, NovaAnalysisResponse
from app.services.document_service import DocumentService

router = APIRouter()
document_service = DocumentService()


@router.post("/analyze", response_model=NovaAnalysisResponse)
async def analyze_document(payload: AnalyzeDocumentRequest) -> NovaAnalysisResponse:
    try:
        return await document_service.analyze_document(
            s3_key=payload.s3Key,
            bucket=payload.bucket,
            doc_id=payload.docId,
        )
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Document analysis failed: {exc}") from exc
