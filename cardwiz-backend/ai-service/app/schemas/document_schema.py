from enum import Enum
from typing import List, Optional

from pydantic import BaseModel, Field

class RewardType(str, Enum):
    CASHBACK = "CASHBACK"
    POINTS = "POINTS"
    MILES = "MILES"

class ExtractedRule(BaseModel):
    cardName: str = Field(..., description="The name of the credit card found in the doc")
    category: str = Field(..., description="The spending category (e.g., Grocery, Dining)")
    rewardRate: float = Field(..., description="The percentage or multiplier value")
    rewardType: RewardType
    conditions: Optional[str] = Field(None, description="Any limit or merchant restrictions")

class DocumentMetadata(BaseModel):
    docId: int
    sourceS3: str
    modelUsed: str = "amazon.nova-2-pro-v1:0"

class NovaAnalysisResponse(BaseModel):
    documentMetadata: DocumentMetadata
    extractedRules: List[ExtractedRule]
    aiSummary: str = Field(..., description="Natural language summary of the document's value")


class AnalyzeDocumentRequest(BaseModel):
    docId: int
    s3Key: str
    bucket: Optional[str] = None
