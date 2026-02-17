package com.cardwiz.userservice.dtos;

import com.cardwiz.userservice.models.DocumentStatus;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCardResponse {
    private Long id;
    private String cardName;
    private String issuer;
    private String network;
    private String lastFourDigits;
    private boolean active;
    private DocumentStatus docStatus;
    private String docS3Key;
    private Instant lastAnalyzedAt;
}
