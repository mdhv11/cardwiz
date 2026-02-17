package com.cardwiz.userservice.services;

import com.cardwiz.userservice.dtos.IngestRequestDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentIngestEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topic.document-ingest}")
    private String ingestTopic;

    public void publish(IngestRequestDTO payload) {
        final String serialized;
        try {
            serialized = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to serialize ingest event", ex);
        }

        String key = payload.getCardId() == null ? "unknown" : String.valueOf(payload.getCardId());
        try {
            kafkaTemplate.send(ingestTopic, key, serialized).get(5, TimeUnit.SECONDS);
            log.info("Published ingest event for cardId={} to topic={}", payload.getCardId(), ingestTopic);
        } catch (Exception ex) {
            log.error("Failed publishing ingest event for cardId={}: {}", payload.getCardId(), ex.getMessage());
            throw new RuntimeException("Failed to publish ingest event to Kafka", ex);
        }
    }
}
