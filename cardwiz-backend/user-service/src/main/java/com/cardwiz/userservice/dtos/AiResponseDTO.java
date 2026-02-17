package com.cardwiz.userservice.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiResponseDTO {
    private DocumentMetadataDTO documentMetadata;
    private List<ExtractedRuleDTO> extractedRules;
    private String aiSummary;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentMetadataDTO {
        private Long docId;
        private String sourceS3;
        private String modelUsed;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractedRuleDTO {
        private String cardName;
        private String category;
        private Double rewardRate;
        private String rewardType;
        private Double pointsPerUnit;
        private Double spendUnit;
        private Double pointValueRupees;
        private Double effectiveRewardPercentage;
        private String conditions;
    }
}
