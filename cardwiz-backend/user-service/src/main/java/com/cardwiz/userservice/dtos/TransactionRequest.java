package com.cardwiz.userservice.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRequest {
    private BigDecimal amount;
    private String merchant;
    private String category;
    private String currency;
    private LocalDate transactionDate;
    private Long suggestedCardId;
    private Long actualCardId;
    private BigDecimal potentialSavings;
}
