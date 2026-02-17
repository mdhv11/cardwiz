package com.cardwiz.userservice.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdvisorMessageResponse {
    private Long id;
    private String sender;
    private String text;
    private LocalDateTime createdAt;
}
