package com.cardwiz.userservice.dtos;

import com.cardwiz.userservice.models.ProcessingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentJobStatusDTO {
    private Long documentId;
    private ProcessingStatus status;
    private String aiSummary;
    private Long cardId;
}
