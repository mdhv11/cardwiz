package com.cardwiz.userservice.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResponseDTO {
    private TransactionResponse transaction;
    private RecommendationDTO recommendation;
    private String message;
}
