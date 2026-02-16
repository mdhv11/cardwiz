package com.cardwiz.userservice.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentMetadata {
    private Long docId;
    private String sourceS3;
    private String modelUsed;
}
