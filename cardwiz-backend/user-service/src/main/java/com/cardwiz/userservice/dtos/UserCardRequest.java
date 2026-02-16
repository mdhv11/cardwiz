package com.cardwiz.userservice.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCardRequest {
    private String cardName;
    private String issuer;
    private String network;
    private String lastFourDigits;
    private boolean active = true;
}
