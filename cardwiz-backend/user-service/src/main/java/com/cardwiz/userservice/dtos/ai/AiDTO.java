package com.cardwiz.userservice.dtos.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

public class AiDTO {
    private AiDTO() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentAnalyzeRequest {
        private String documentType;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendationBridgeRequest {
        private String merchantName;
        private String category;
        private BigDecimal transactionAmount;
    }
}
