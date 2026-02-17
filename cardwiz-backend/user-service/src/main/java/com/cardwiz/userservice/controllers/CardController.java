package com.cardwiz.userservice.controllers;

import com.cardwiz.userservice.dtos.AiResponseDTO;
import com.cardwiz.userservice.dtos.DocumentIngestionResponseDTO;
import com.cardwiz.userservice.dtos.DocumentJobStatusDTO;
import com.cardwiz.userservice.dtos.IngestCallbackRequestDTO;
import com.cardwiz.userservice.dtos.IngestRequestDTO;
import com.cardwiz.userservice.dtos.RecommendationDTO;
import com.cardwiz.userservice.dtos.RecommendationRequestDTO;
import com.cardwiz.userservice.dtos.UserCardRequest;
import com.cardwiz.userservice.dtos.UserCardResponse;
import com.cardwiz.userservice.dtos.UserResponseDTO;
import com.cardwiz.userservice.dtos.TransactionResponse;
import com.cardwiz.userservice.models.UploadedDocument;
import com.cardwiz.userservice.services.AiServiceClient;
import com.cardwiz.userservice.services.CardService;
import com.cardwiz.userservice.services.DocumentIngestEventPublisher;
import com.cardwiz.userservice.services.ImageUploadService;
import com.cardwiz.userservice.services.TransactionService;
import com.cardwiz.userservice.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
@Slf4j
public class CardController {
    private static final double DEFAULT_POINT_VALUE_RUPEES = 0.25d;

    private final CardService cardService;
    private final UserService userService;
    private final ImageUploadService imageUploadService;
    private final AiServiceClient aiServiceClient;
    private final TransactionService transactionService;
    private final DocumentIngestEventPublisher ingestEventPublisher;

    @Value("${app.internal.ai-callback-secret}")
    private String aiCallbackSecret;

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
        return ResponseEntity.ok(ingestDocument(userId, file, documentType, null));
    }

    @PostMapping({"/{cardId}/documents/analyze", "/{cardId}/upload-docs"})
    public ResponseEntity<DocumentIngestionResponseDTO> analyzeDocumentForCard(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long cardId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "documentType", defaultValue = "CARD_TNC") String documentType) {
        UserResponseDTO current = userService.getUserProfileByEmail(userDetails.getUsername());
        Long userId = Long.valueOf(current.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(queueCardDocumentIngestion(userId, cardId, file, documentType));
    }

    @PostMapping("/internal/ingestion-callback")
    public ResponseEntity<Void> handleIngestionCallback(
            @RequestHeader(value = "X-AI-CALLBACK-SECRET", required = false) String secret,
            @RequestBody IngestCallbackRequestDTO callback) {
        if (secret == null || !secret.equals(aiCallbackSecret)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid callback secret");
        }
        if (callback.getCardId() == null || callback.getDocumentId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cardId and documentId are required");
        }

        String status = callback.getStatus() == null ? "" : callback.getStatus().toUpperCase(Locale.ROOT);
        if ("COMPLETED".equals(status)) {
            cardService.markDocumentComplete(callback.getDocumentId(), callback.getAiSummary());
            cardService.markCardDocumentCompleted(callback.getCardId());
            return ResponseEntity.ok().build();
        }

        cardService.markDocumentFailed(callback.getDocumentId());
        cardService.markCardDocumentFailed(callback.getCardId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/knowledge-coverage")
    public ResponseEntity<Map<Long, Boolean>> getKnowledgeCoverage(@AuthenticationPrincipal UserDetails userDetails) {
        UserResponseDTO current = userService.getUserProfileByEmail(userDetails.getUsername());
        Long userId = Long.valueOf(current.getId());
        List<Long> activeCardIds = cardService.getActiveCardIds(userId);
        if (activeCardIds.isEmpty()) {
            return ResponseEntity.ok(Map.of());
        }

        Set<Long> covered = new HashSet<>();
        var coverage = aiServiceClient.getEmbeddingCoverage(activeCardIds);
        if (coverage != null && coverage.getCoveredCardIds() != null) {
            covered.addAll(coverage.getCoveredCardIds());
        }

        Map<Long, Boolean> payload = activeCardIds.stream()
                .collect(Collectors.toMap(cardId -> cardId, covered::contains));
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/documents/{documentId}/status")
    public ResponseEntity<DocumentJobStatusDTO> getDocumentJobStatus(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long documentId) {
        UserResponseDTO current = userService.getUserProfileByEmail(userDetails.getUsername());
        Long userId = Long.valueOf(current.getId());
        return ResponseEntity.ok(cardService.getDocumentJobStatus(userId, documentId));
    }

    private DocumentIngestionResponseDTO ingestDocument(Long userId, MultipartFile file, String documentType, Long forcedCardId) {
        String s3Key = imageUploadService.uploadDocument(file, userId);
        UploadedDocument document = cardService.createDocumentRecord(userId, s3Key, documentType);

        try {
            AiResponseDTO analysis = aiServiceClient.analyzeDocument(
                    imageUploadService.getDocumentBucketName(),
                    s3Key,
                    document.getId()
            );
            syncExtractedRules(userId, document.getId(), analysis, forcedCardId);
            UploadedDocument completed = cardService.markDocumentComplete(document.getId(), analysis.getAiSummary());
            return DocumentIngestionResponseDTO.builder()
                    .documentId(completed.getId())
                    .s3Key(completed.getS3Url())
                    .documentType(completed.getDocumentType())
                    .status(completed.getStatus())
                    .aiSummary(completed.getAiSummary())
                    .analysis(analysis)
                    .build();
        } catch (RuntimeException ex) {
            cardService.markDocumentFailed(document.getId());
            throw ex;
        }
    }

    private DocumentIngestionResponseDTO queueCardDocumentIngestion(Long userId, Long cardId, MultipartFile file, String documentType) {
        cardService.getCard(userId, cardId);
        String s3Key = imageUploadService.uploadDocument(file, userId);
        UploadedDocument document = cardService.createDocumentRecord(userId, s3Key, documentType);
        cardService.markCardDocumentProcessing(userId, cardId, s3Key);

        IngestRequestDTO payload = new IngestRequestDTO(
                document.getId(),
                cardId,
                userId,
                s3Key,
                imageUploadService.getDocumentBucketName(),
                documentType
        );

        try {
            ingestEventPublisher.publish(payload);
        } catch (RuntimeException ex) {
            cardService.markDocumentFailed(document.getId());
            cardService.markCardDocumentFailed(cardId);
            throw ex;
        }

        return DocumentIngestionResponseDTO.builder()
                .documentId(document.getId())
                .s3Key(document.getS3Url())
                .documentType(document.getDocumentType())
                .status(document.getStatus())
                .aiSummary("Processing started. AI will update status asynchronously.")
                .build();
    }

    @PostMapping("/recommendations")
    public ResponseEntity<RecommendationDTO> getRecommendation(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody RecommendationRequestDTO request) {
        UserResponseDTO current = userService.getUserProfileByEmail(userDetails.getUsername());
        Long userId = Long.valueOf(current.getId());
        String historyContext = buildRecentValidationContext(userId);
        List<Long> eligibleCardIds = resolveEligibleCardIds(userId, request.getAvailableCardIds());

        RecommendationRequestDTO enrichedRequest = new RecommendationRequestDTO(
                userId,
                request.getMerchantName(),
                request.getCategory(),
                request.getTransactionAmount(),
                request.getCurrency(),
                mergeContextNotes(request.getContextNotes(), historyContext),
                eligibleCardIds
        );

        return ResponseEntity.ok(aiServiceClient.getRecommendation(enrichedRequest));
    }

    private String mergeContextNotes(String requestContext, String historyContext) {
        boolean hasRequest = requestContext != null && !requestContext.isBlank();
        boolean hasHistory = historyContext != null && !historyContext.isBlank();
        if (hasRequest && hasHistory) {
            return requestContext + " | " + historyContext;
        }
        if (hasRequest) {
            return requestContext;
        }
        return hasHistory ? historyContext : null;
    }

    private List<Long> resolveEligibleCardIds(Long userId, List<Long> requestedCardIds) {
        List<Long> activeCardIds = cardService.getActiveCardIds(userId);
        if (requestedCardIds == null || requestedCardIds.isEmpty()) {
            return activeCardIds;
        }

        Set<Long> allowed = Set.copyOf(activeCardIds);
        return requestedCardIds.stream()
                .filter(Objects::nonNull)
                .filter(allowed::contains)
                .distinct()
                .toList();
    }

    private String buildRecentValidationContext(Long userId) {
        List<TransactionResponse> recent = transactionService.listTransactions(userId).stream()
                .sorted(
                        Comparator.comparing(
                                TransactionResponse::getTransactionDate,
                                Comparator.nullsLast(Comparator.reverseOrder())
                        ).thenComparing(
                                TransactionResponse::getId,
                                Comparator.nullsLast(Comparator.reverseOrder())
                        )
                )
                .limit(5)
                .toList();

        if (recent.isEmpty()) {
            return "";
        }

        return recent.stream()
                .map(tx -> {
                    String merchant = tx.getMerchant() == null ? "unknown-merchant" : tx.getMerchant();
                    String category = tx.getCategory() == null ? "general" : tx.getCategory();
                    String currency = tx.getCurrency() == null ? "INR" : tx.getCurrency();
                    String amount = tx.getAmount() == null ? "0" : tx.getAmount().toPlainString();
                    return merchant + ":" + category + ":" + currency + ":" + amount;
                })
                .reduce((left, right) -> left + " ; " + right)
                .orElse("");
    }

    private void syncExtractedRules(Long userId, Long documentId, AiResponseDTO analysis, Long forcedCardId) {
        if (analysis == null || analysis.getExtractedRules() == null || analysis.getExtractedRules().isEmpty()) {
            return;
        }

        List<UserCardResponse> userCards = cardService.getCardsForUser(userId).stream()
                .filter(UserCardResponse::isActive)
                .toList();
        if (userCards.isEmpty()) {
            return;
        }

        Long fallbackCardId = forcedCardId != null ? forcedCardId : userCards.get(0).getId();

        for (int index = 0; index < analysis.getExtractedRules().size(); index++) {
            AiResponseDTO.ExtractedRuleDTO rule = analysis.getExtractedRules().get(index);
            Long mappedCardId = forcedCardId != null
                    ? forcedCardId
                    : matchCardId(rule.getCardName(), userCards).orElse(fallbackCardId);
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
        String cardName = rule.getCardName() == null ? "unknown" : rule.getCardName();
        String category = rule.getCategory() == null ? "general" : rule.getCategory();
        String rewardType = rule.getRewardType() == null ? "REWARD" : rule.getRewardType();
        String rewardRate = rule.getRewardRate() == null ? "0" : String.valueOf(rule.getRewardRate());
        String pointsPerUnit = rule.getPointsPerUnit() == null ? "null" : String.valueOf(rule.getPointsPerUnit());
        String spendUnit = rule.getSpendUnit() == null ? "null" : String.valueOf(rule.getSpendUnit());
        String pointValueRupees = rule.getPointValueRupees() == null ? "null" : String.valueOf(rule.getPointValueRupees());
        double effectivePctValue = deriveEffectiveRewardPercentage(rule);
        String effectivePct = String.valueOf(effectivePctValue);
        String conditions = rule.getConditions() == null ? "none" : rule.getConditions().replace(";", ",");

        return "card_name=" + cardName
                + ";category=" + category
                + ";reward_type=" + rewardType
                + ";reward_rate=" + rewardRate
                + ";points_per_unit=" + pointsPerUnit
                + ";spend_unit=" + spendUnit
                + ";point_value_rupees=" + pointValueRupees
                + ";effective_reward_percentage=" + effectivePct
                + ";conditions=" + conditions;
    }

    private double deriveEffectiveRewardPercentage(AiResponseDTO.ExtractedRuleDTO rule) {
        if (rule.getEffectiveRewardPercentage() != null && rule.getEffectiveRewardPercentage() > 0) {
            return rule.getEffectiveRewardPercentage();
        }

        String rewardType = rule.getRewardType() == null ? "" : rule.getRewardType().toUpperCase(Locale.ROOT);
        if ("CASHBACK".equals(rewardType) && rule.getRewardRate() != null && rule.getRewardRate() > 0) {
            return roundTwoDecimals(rule.getRewardRate());
        }

        if ("POINTS".equals(rewardType)) {
            Double pointsPerUnit = rule.getPointsPerUnit();
            Double spendUnit = rule.getSpendUnit();
            Double pointValue = rule.getPointValueRupees() != null ? rule.getPointValueRupees() : DEFAULT_POINT_VALUE_RUPEES;
            if (pointsPerUnit != null && spendUnit != null && spendUnit > 0 && pointValue > 0) {
                double pct = (pointsPerUnit * pointValue / spendUnit) * 100.0d;
                return roundTwoDecimals(pct);
            }
        }

        if (rule.getRewardRate() != null && rule.getRewardRate() > 0) {
            return roundTwoDecimals(rule.getRewardRate());
        }
        return 0.0d;
    }

    private double roundTwoDecimals(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }
}
