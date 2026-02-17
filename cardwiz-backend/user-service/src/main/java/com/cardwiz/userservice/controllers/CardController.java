package com.cardwiz.userservice.controllers;

import com.cardwiz.userservice.dtos.AiResponseDTO;
import com.cardwiz.userservice.dtos.DocumentIngestionResponseDTO;
import com.cardwiz.userservice.dtos.RecommendationDTO;
import com.cardwiz.userservice.dtos.RecommendationRequestDTO;
import com.cardwiz.userservice.dtos.UserCardRequest;
import com.cardwiz.userservice.dtos.UserCardResponse;
import com.cardwiz.userservice.dtos.UserResponseDTO;
import com.cardwiz.userservice.models.UploadedDocument;
import com.cardwiz.userservice.services.AiServiceClient;
import com.cardwiz.userservice.services.CardService;
import com.cardwiz.userservice.services.ImageUploadService;
import com.cardwiz.userservice.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
@Slf4j
public class CardController {

    private final CardService cardService;
    private final UserService userService;
    private final ImageUploadService imageUploadService;
    private final AiServiceClient aiServiceClient;

    @GetMapping
    public ResponseEntity<List<UserCardResponse>> listCards(@AuthenticationPrincipal UserDetails userDetails) {
        UserResponseDTO current = userService.getUserProfileByEmail(userDetails.getUsername());
        return ResponseEntity.ok(cardService.getCardsForUser(Long.valueOf(current.getId())));
    }

    @GetMapping("/{cardId}")
    public ResponseEntity<UserCardResponse> getCard(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long cardId) {
        UserResponseDTO current = userService.getUserProfileByEmail(userDetails.getUsername());
        return ResponseEntity.ok(cardService.getCard(Long.valueOf(current.getId()), cardId));
    }

    @PostMapping
    public ResponseEntity<UserCardResponse> createCard(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody UserCardRequest request) {
        UserResponseDTO current = userService.getUserProfileByEmail(userDetails.getUsername());
        return ResponseEntity.ok(cardService.createCard(Long.valueOf(current.getId()), request));
    }

    @PutMapping("/{cardId}")
    public ResponseEntity<UserCardResponse> updateCard(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long cardId,
            @RequestBody UserCardRequest request) {
        UserResponseDTO current = userService.getUserProfileByEmail(userDetails.getUsername());
        return ResponseEntity.ok(cardService.updateCard(Long.valueOf(current.getId()), cardId, request));
    }

    @DeleteMapping("/{cardId}")
    public ResponseEntity<Void> deleteCard(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long cardId) {
        UserResponseDTO current = userService.getUserProfileByEmail(userDetails.getUsername());
        cardService.deleteCard(Long.valueOf(current.getId()), cardId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/documents/analyze")
    public ResponseEntity<DocumentIngestionResponseDTO> analyzeDocument(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "documentType", defaultValue = "STATEMENT") String documentType) {
        UserResponseDTO current = userService.getUserProfileByEmail(userDetails.getUsername());
        Long userId = Long.valueOf(current.getId());

        String s3Key = imageUploadService.uploadDocument(file, userId);
        UploadedDocument document = cardService.createDocumentRecord(userId, s3Key, documentType);

        try {
            AiResponseDTO analysis = aiServiceClient.analyzeDocument(
                    imageUploadService.getDocumentBucketName(),
                    s3Key,
                    document.getId()
            );
            syncExtractedRules(userId, document.getId(), analysis);
            UploadedDocument completed = cardService.markDocumentComplete(document.getId(), analysis.getAiSummary());
            return ResponseEntity.ok(
                    DocumentIngestionResponseDTO.builder()
                            .documentId(completed.getId())
                            .s3Key(completed.getS3Url())
                            .documentType(completed.getDocumentType())
                            .status(completed.getStatus())
                            .aiSummary(completed.getAiSummary())
                            .analysis(analysis)
                            .build()
            );
        } catch (RuntimeException ex) {
            cardService.markDocumentFailed(document.getId());
            throw ex;
        }
    }

    @PostMapping("/recommendations")
    public ResponseEntity<RecommendationDTO> getRecommendation(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody RecommendationRequestDTO request) {
        UserResponseDTO current = userService.getUserProfileByEmail(userDetails.getUsername());
        Long userId = Long.valueOf(current.getId());

        RecommendationRequestDTO enrichedRequest = new RecommendationRequestDTO(
                userId,
                request.getMerchantName(),
                request.getCategory(),
                request.getTransactionAmount(),
                cardService.getActiveCardIds(userId)
        );

        return ResponseEntity.ok(aiServiceClient.getRecommendation(enrichedRequest));
    }

    private void syncExtractedRules(Long userId, Long documentId, AiResponseDTO analysis) {
        if (analysis == null || analysis.getExtractedRules() == null || analysis.getExtractedRules().isEmpty()) {
            return;
        }

        List<UserCardResponse> userCards = cardService.getCardsForUser(userId).stream()
                .filter(UserCardResponse::isActive)
                .toList();
        if (userCards.isEmpty()) {
            return;
        }

        Long fallbackCardId = userCards.get(0).getId();

        for (int index = 0; index < analysis.getExtractedRules().size(); index++) {
            AiResponseDTO.ExtractedRuleDTO rule = analysis.getExtractedRules().get(index);
            Long mappedCardId = matchCardId(rule.getCardName(), userCards).orElse(fallbackCardId);
            Integer ruleId = Objects.hash(documentId, index, mappedCardId, rule.getCategory(), rule.getRewardType());
            String contentText = buildRuleContentText(rule);

            try {
                aiServiceClient.syncEmbedding(ruleId, mappedCardId, contentText);
            } catch (RuntimeException ex) {
                log.warn(
                        "Failed syncing embedding for documentId={}, ruleIndex={}, mappedCardId={}: {}",
                        documentId,
                        index,
                        mappedCardId,
                        ex.getMessage()
                );
            }
        }
    }

    private Optional<Long> matchCardId(String extractedCardName, List<UserCardResponse> userCards) {
        if (extractedCardName == null || extractedCardName.isBlank()) {
            return Optional.empty();
        }
        String needle = extractedCardName.toLowerCase(Locale.ROOT);

        return userCards.stream()
                .filter(card -> card.getCardName() != null)
                .filter(card -> {
                    String haystack = card.getCardName().toLowerCase(Locale.ROOT);
                    return haystack.contains(needle) || needle.contains(haystack);
                })
                .map(UserCardResponse::getId)
                .findFirst();
    }

    private String buildRuleContentText(AiResponseDTO.ExtractedRuleDTO rule) {
        String category = rule.getCategory() == null ? "general" : rule.getCategory();
        String rewardRate = rule.getRewardRate() == null ? "0" : String.valueOf(rule.getRewardRate());
        String rewardType = rule.getRewardType() == null ? "REWARD" : rule.getRewardType();
        String conditions = rule.getConditions() == null ? "none" : rule.getConditions();
        return category + " " + rewardRate + " " + rewardType + " " + conditions;
    }
}
