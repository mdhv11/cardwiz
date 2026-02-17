package com.cardwiz.userservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationDTO {
    private CardRecommendation bestOption;
    private List<CardRecommendation> alternatives;
    private String semanticContext;
    @JsonProperty("recommendation_id")
    private String recommendationId;
    @JsonProperty("transaction_context")
    private TransactionContext transactionContext;
    @JsonProperty("best_card")
    private BestCard bestCard;
    @JsonProperty("comparison_table")
    private List<ComparisonRow> comparisonTable;
    @JsonProperty("covered_card_ids")
    private List<Long> coveredCardIds;
    @JsonProperty("missing_card_ids")
    private List<Long> missingCardIds;
    @JsonProperty("has_sufficient_data")
    private Boolean hasSufficientData;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CardRecommendation {
        private Long cardId;
        private String cardName;
        private String estimatedReward;
        private String reasoning;
        private Double confidenceScore;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionContext {
        private String merchant;
        private String category;
        @JsonProperty("spend_amount")
        private Double spendAmount;
        private String currency;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BestCard {
        private Long id;
        private String name;
        private String status;
        private Rewards rewards;
        @JsonProperty("calculation_logic")
        private String calculationLogic;
        private List<String> reasoning;
        private String warning;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Rewards {
        @JsonProperty("estimated_value")
        private Double estimatedValue;
        @JsonProperty("value_unit")
        private String valueUnit;
        @JsonProperty("effective_percentage")
        private Double effectivePercentage;
        @JsonProperty("reward_type")
        private String rewardType;
        @JsonProperty("raw_points_earned")
        private Double rawPointsEarned;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComparisonRow {
        @JsonProperty("card_name")
        private String cardName;
        @JsonProperty("effective_percentage")
        private Double effectivePercentage;
        @JsonProperty("estimated_value")
        private Double estimatedValue;
        private String verdict;
    }
}
