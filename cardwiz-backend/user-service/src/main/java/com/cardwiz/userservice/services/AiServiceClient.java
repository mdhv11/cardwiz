package com.cardwiz.userservice.services;

import com.cardwiz.userservice.dtos.AiResponseDTO;
import com.cardwiz.userservice.dtos.AnalyzeRequestDTO;
import com.cardwiz.userservice.dtos.RecommendationDTO;
import com.cardwiz.userservice.dtos.RecommendationRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class AiServiceClient {

    private final RestClient.Builder restClientBuilder;

    // The service name registered in Eureka (UPPERCASE usually works best)
    private static final String AI_SERVICE_URL = "http://ai-service";

    public AiResponseDTO analyzeDocument(String bucket, String s3Key, Long docId) {
        return restClientBuilder.build()
                .post()
                .uri(AI_SERVICE_URL + "/ai/v1/documents/analyze")
                .body(new AnalyzeRequestDTO(docId, s3Key, bucket))
                .retrieve()
                .body(AiResponseDTO.class);
    }

    @Cacheable(
            cacheNames = "aiRecommendations",
            key = "T(java.util.Objects).hash(#request.userId, #request.merchantName, #request.category, #request.transactionAmount, #request.availableCardIds)"
    )
    public RecommendationDTO getRecommendation(RecommendationRequestDTO request) {
        return restClientBuilder.build()
                .post()
                .uri(AI_SERVICE_URL + "/ai/v1/recommend/rank")
                .body(request)
                .retrieve()
                .body(RecommendationDTO.class);
    }
}
