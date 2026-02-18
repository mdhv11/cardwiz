package com.cardwiz.userservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatementMissedSavingsResponseDTO {
    @JsonProperty("statement_s3_key")
    private String statementS3Key;
    private Summary summary;
    private List<TransactionRow> transactions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        @JsonProperty("transactions_analyzed")
        private Integer transactionsAnalyzed;
        @JsonProperty("total_spend")
        private Double totalSpend;
        @JsonProperty("total_actual_rewards")
        private Double totalActualRewards;
        @JsonProperty("total_optimal_rewards")
        private Double totalOptimalRewards;
        @JsonProperty("total_missed_savings")
        private Double totalMissedSavings;
        private String currency;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionRow {
        private String date;
        private String merchant;
        private Double amount;
        @JsonProperty("actual_card_id")
        private Long actualCardId;
        @JsonProperty("actual_card_name")
        private String actualCardName;
        @JsonProperty("actual_reward_value")
        private Double actualRewardValue;
        @JsonProperty("actual_reward_source")
        private String actualRewardSource;
        @JsonProperty("optimal_card_id")
        private Long optimalCardId;
        @JsonProperty("optimal_card_name")
        private String optimalCardName;
        @JsonProperty("optimal_reward_value")
        private Double optimalRewardValue;
        @JsonProperty("missed_value")
        private Double missedValue;
    }
}
