package com.cardwiz.userservice.dtos;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class UserResponseDTO {
    private String id;
    private String email;
    private String firstName;
    private String lastName;
    private String profileImageUrl;
}
