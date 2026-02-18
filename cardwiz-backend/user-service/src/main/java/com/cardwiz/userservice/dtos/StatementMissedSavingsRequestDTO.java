package com.cardwiz.userservice.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatementMissedSavingsRequestDTO {
    private Long userId;
    private String statementS3Key;
    private Long actualCardId;
    private List<Long> availableCardIds;
    private String bucket;
    private String currency;
    private String contextNotes;
    private Integer limitTransactions;
}
