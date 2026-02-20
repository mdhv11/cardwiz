package com.cardwiz.userservice.dtos;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdvisorMessageCreateRequest {
    private String sender;
    private String text;
    private String type;
    private JsonNode payload;
}
