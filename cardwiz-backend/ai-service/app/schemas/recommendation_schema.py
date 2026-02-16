from pydantic import BaseModel, Field
from typing import List, Optional

class RecommendationRequest(BaseModel):
    userId: int
    merchantName: str = Field(..., example="Starbucks")
    category: Optional[str] = Field(None, example="Coffee Shop")
    transactionAmount: Optional[float] = None
    # List of card IDs the user currently owns, filtered by the Spring service
    availableCardIds: List[int] 

class CardRecommendation(BaseModel):
    cardId: int
    cardName: str
    estimatedReward: str = Field(..., description="e.g., '5% Cashback' or '10x Points'")
    reasoning: str = Field(..., description="AI-generated explanation of why this card is best")
    confidenceScore: float = Field(..., ge=0, le=1)

class RecommendationResponse(BaseModel):
    bestOption: CardRecommendation
    alternatives: List[CardRecommendation] = Field(default_factory=list)
    semanticContext: Optional[str] = Field(None, description="The context used for vector search")


class EmbeddingSyncRequest(BaseModel):
    ruleId: int
    cardId: int
    contentText: str


class EmbeddingSyncResponse(BaseModel):
    status: str
    ruleId: int
