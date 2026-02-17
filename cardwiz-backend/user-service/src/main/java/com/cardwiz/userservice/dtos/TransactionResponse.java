package com.cardwiz.userservice.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class TransactionResponse {
    private Long id;
    private BigDecimal amount;
    private String merchant;
    private String category;
    private String currency;
    private LocalDate transactionDate;
    private Long suggestedCardId;
    private Long actualCardId;
}
