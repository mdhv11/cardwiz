package com.cardwiz.userservice.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IngestCallbackRequestDTO {
    private Long documentId;
    private Long cardId;
    private String status; // COMPLETED / FAILED
    private String aiSummary;
    private String error;
}
