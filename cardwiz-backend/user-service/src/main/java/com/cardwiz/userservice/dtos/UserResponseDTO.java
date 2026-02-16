package com.cardwiz.userservice.dtos;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class UserResponseDTO {
    private String id;
    private String name;
    private String username;
    private String email;
    private String profileImageURL;
}