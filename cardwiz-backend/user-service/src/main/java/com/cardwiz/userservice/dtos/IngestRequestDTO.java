package com.cardwiz.userservice.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IngestRequestDTO {
    private Long documentId;
    private Long cardId;
    private Long userId;
    private String s3Key;
    private String bucketName;
    private String documentType;
}
