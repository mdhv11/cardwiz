package com.cardwiz.userservice.dtos;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

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
}
