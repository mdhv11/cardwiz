package com.cardwiz.userservice.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationRequestDTO {
    private String merchant;
    private BigDecimal amount;
    private String category;
    private String currency;
    private LocalDate transactionDate;
    private Long actualCardId;
    private String contextNotes;
}
