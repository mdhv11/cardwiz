package com.cardwiz.userservice.dtos.Auth;

import com.cardwiz.userservice.dtos.UserResponseDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthenticationResponse {
    private String token;
    private String userId;
    private String username;
    private UserResponseDTO user;
}