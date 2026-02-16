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
public class DocumentIngestionResponseDTO {
    private Long documentId;
    private String s3Key;
    private String documentType;
    private ProcessingStatus status;
    private String aiSummary;
    private AiResponseDTO analysis;
}
