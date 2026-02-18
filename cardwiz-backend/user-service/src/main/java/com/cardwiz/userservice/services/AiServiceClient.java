package com.cardwiz.userservice.services;

import com.cardwiz.userservice.dtos.AiResponseDTO;
import com.cardwiz.userservice.dtos.AnalyzeRequestDTO;
import com.cardwiz.userservice.dtos.EmbeddingSyncRequestDTO;
import com.cardwiz.userservice.dtos.EmbeddingSyncResponseDTO;
import com.cardwiz.userservice.dtos.EmbeddingCoverageRequestDTO;
import com.cardwiz.userservice.dtos.EmbeddingCoverageResponseDTO;
import com.cardwiz.userservice.dtos.RecommendationDTO;
import com.cardwiz.userservice.dtos.RecommendationRequestDTO;
import com.cardwiz.userservice.dtos.StatementMissedSavingsRequestDTO;
import com.cardwiz.userservice.dtos.StatementMissedSavingsResponseDTO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiServiceClient {

    @Qualifier("loadBalancedRestClientBuilder")
    private final RestClient.Builder restClientBuilder;

    @Value("${AI_SERVICE_URL:http://ai-service}")
    private String aiServiceUrl;

    @PostConstruct
    public void normalizeAiServiceUrl() {
        String configuredUrl = aiServiceUrl;
        try {
            URI uri = URI.create(configuredUrl);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            Set<String> loopbackHosts = Set.of("localhost", "127.0.0.1", "::1");
            if (loopbackHosts.contains(host)) {
                aiServiceUrl = "http://ai-service";
                log.warn("AI_SERVICE_URL={} points to loopback; using Eureka service id URL {}", configuredUrl, aiServiceUrl);
            } else {
                log.info("AI_SERVICE_URL resolved to {}", aiServiceUrl);
            }
        } catch (Exception ex) {
            aiServiceUrl = "http://ai-service";
            log.warn("Invalid AI_SERVICE_URL='{}'. Falling back to {}", configuredUrl, aiServiceUrl);
        }
    }

    public AiResponseDTO analyzeDocument(String bucket, String s3Key, Long docId) {
        return restClientBuilder.build()
                .post()
                .uri(aiServiceUrl + "/ai/v1/documents/analyze")
                .body(new AnalyzeRequestDTO(docId, s3Key, bucket))
                .retrieve()
                .body(AiResponseDTO.class);
    }

    @Cacheable(
            cacheNames = "aiRecommendationsV2",
            key = "T(java.util.Objects).hash(#request.userId, #request.merchantName, #request.category, #request.transactionAmount, #request.currency, #request.contextNotes, #request.availableCardIds)"
    )
    public RecommendationDTO getRecommendation(RecommendationRequestDTO request) {
        return restClientBuilder.build()
                .post()
                .uri(aiServiceUrl + "/ai/v1/recommend/rank")
                .body(request)
                .retrieve()
                .body(RecommendationDTO.class);
    }

    public EmbeddingSyncResponseDTO syncEmbedding(Integer ruleId, Long cardId, String contentText) {
        return restClientBuilder.build()
                .post()
                .uri(aiServiceUrl + "/ai/v1/embeddings/sync")
                .body(new EmbeddingSyncRequestDTO(ruleId, cardId, contentText))
                .retrieve()
                .body(EmbeddingSyncResponseDTO.class);
    }

    public EmbeddingCoverageResponseDTO getEmbeddingCoverage(List<Long> cardIds) {
        return restClientBuilder.build()
                .post()
                .uri(aiServiceUrl + "/ai/v1/embeddings/coverage")
                .body(new EmbeddingCoverageRequestDTO(cardIds))
                .retrieve()
                .body(EmbeddingCoverageResponseDTO.class);
    }

    public StatementMissedSavingsResponseDTO analyzeStatementMissedSavings(StatementMissedSavingsRequestDTO request) {
        return restClientBuilder.build()
                .post()
                .uri(aiServiceUrl + "/ai/v1/recommend/statement-missed-savings")
                .body(request)
                .retrieve()
                .body(StatementMissedSavingsResponseDTO.class);
    }
}
