from pydantic import BaseModel, Field
from typing import List, Optional

class RecommendationRequest(BaseModel):
    userId: int
    merchantName: str = Field(..., example="Starbucks")
    category: Optional[str] = Field(None, example="Coffee Shop")
    transactionAmount: Optional[float] = None
    currency: Optional[str] = Field("INR", example="INR")
    contextNotes: Optional[str] = None
    # List of card IDs the user currently owns, filtered by the Spring service
    availableCardIds: List[int] 

class CardRecommendation(BaseModel):
    cardId: int
    cardName: str
    estimatedReward: str = Field(..., description="e.g., '5% Cashback' or '10x Points'")
    reasoning: str = Field(..., description="AI-generated explanation of why this card is best")
    confidenceScore: float = Field(..., ge=0, le=1)


class TransactionContext(BaseModel):
    merchant: str
    category: str
    spend_amount: float
    currency: str


class RecommendationRewards(BaseModel):
    estimated_value: float
    value_unit: str
    effective_percentage: float
    reward_type: str
    raw_points_earned: Optional[float] = None


class BestCardDetails(BaseModel):
    id: int
    name: str
    status: str = "WINNER"
    rewards: RecommendationRewards
    calculation_logic: str
    reasoning: List[str] = Field(default_factory=list)
    warning: Optional[str] = None


class ComparisonCard(BaseModel):
    card_id: Optional[int] = None
    card_name: str
    effective_percentage: float
    estimated_value: float
    verdict: str


class RecommendationResponse(BaseModel):
    bestOption: CardRecommendation
    alternatives: List[CardRecommendation] = Field(default_factory=list)
    semanticContext: Optional[str] = Field(None, description="The context used for vector search")
    recommendation_id: Optional[str] = None
    transaction_context: Optional[TransactionContext] = None
    best_card: Optional[BestCardDetails] = None
    comparison_table: List[ComparisonCard] = Field(default_factory=list)
    covered_card_ids: List[int] = Field(default_factory=list)
    missing_card_ids: List[int] = Field(default_factory=list)
    has_sufficient_data: bool = True


class EmbeddingSyncRequest(BaseModel):
    ruleId: int
    cardId: int
    contentText: str


class EmbeddingSyncResponse(BaseModel):
    status: str
    ruleId: int


class EmbeddingCoverageRequest(BaseModel):
    cardIds: List[int]


class EmbeddingCoverageResponse(BaseModel):
    coveredCardIds: List[int] = Field(default_factory=list)
