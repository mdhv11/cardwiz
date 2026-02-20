package com.cardwiz.userservice.dtos;

import com.fasterxml.jackson.databind.JsonNode;
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
    private String type;
    private JsonNode payload;
    private LocalDateTime createdAt;
}
