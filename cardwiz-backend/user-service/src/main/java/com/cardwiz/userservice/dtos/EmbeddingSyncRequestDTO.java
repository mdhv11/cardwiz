package com.cardwiz.userservice.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingSyncRequestDTO {
    private Integer ruleId;
    private Long cardId;
    private String contentText;
}
