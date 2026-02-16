package com.cardwiz.userservice.dtos;

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
}
