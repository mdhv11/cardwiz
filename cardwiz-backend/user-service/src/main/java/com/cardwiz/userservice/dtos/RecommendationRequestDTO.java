package com.cardwiz.userservice.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationRequestDTO {
    private Long userId;
    private String merchantName;
    private String category;
    private BigDecimal transactionAmount;
    private String currency;
    private String contextNotes;
    private List<Long> availableCardIds;
}
