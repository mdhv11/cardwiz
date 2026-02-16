package com.cardwiz.userservice.dtos;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserCardResponse {
    private Long id;
    private String cardName;
    private String issuer;
    private String network;
    private String lastFourDigits;
    private boolean active;
}
