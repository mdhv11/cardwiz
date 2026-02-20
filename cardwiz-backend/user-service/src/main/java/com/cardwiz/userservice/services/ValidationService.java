package com.cardwiz.userservice.services;

import com.cardwiz.userservice.dtos.RecommendationDTO;
import com.cardwiz.userservice.dtos.RecommendationRequestDTO;
import com.cardwiz.userservice.dtos.TransactionRequest;
import com.cardwiz.userservice.dtos.TransactionResponse;
import com.cardwiz.userservice.dtos.ValidationRequestDTO;
import com.cardwiz.userservice.dtos.ValidationResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ValidationService {

    private final CardService cardService;
    private final TransactionService transactionService;
    private final AiServiceClient aiServiceClient;

    public ValidationResponseDTO processValidation(Long userId, ValidationRequestDTO request) {
        if (request.getMerchant() == null || request.getMerchant().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Merchant is required.");
        }
        if (request.getAmount() == null || request.getAmount().doubleValue() <= 0.0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be greater than zero.");
        }

        List<Long> eligibleCardIds = cardService.getActiveCardIds(userId);
        if (eligibleCardIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active cards found for this user.");
        }

        if (request.getActualCardId() != null && !eligibleCardIds.contains(request.getActualCardId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "actualCardId must be one of your active cards.");
        }

        RecommendationRequestDTO recRequest = new RecommendationRequestDTO(
                userId,
                request.getMerchant().trim(),
                normalizeCategory(request.getCategory()),
                request.getAmount(),
                normalizeCurrency(request.getCurrency()),
                mergeContextNotes(request.getContextNotes(), buildRecentValidationContext(userId)),
                eligibleCardIds
        );

        RecommendationDTO recommendation = aiServiceClient.getRecommendation(recRequest);
        Long suggestedCardId = extractSuggestedCardId(recommendation);
        BigDecimal potentialSavings = estimatePotentialSavings(recommendation, request.getActualCardId());

        TransactionResponse transaction = transactionService.createTransaction(
                userId,
                new TransactionRequest(
                        request.getAmount(),
                        request.getMerchant().trim(),
                        normalizeCategory(request.getCategory()),
                        normalizeCurrency(request.getCurrency()),
                        request.getTransactionDate() != null ? request.getTransactionDate() : LocalDate.now(),
                        suggestedCardId,
                        request.getActualCardId(),
                        potentialSavings
                )
        );

        return ValidationResponseDTO.builder()
                .transaction(transaction)
                .recommendation(recommendation)
                .message("Validation processed and saved.")
                .build();
    }

    private String normalizeCategory(String category) {
        return (category == null || category.isBlank()) ? "general" : category;
    }

    private String normalizeCurrency(String currency) {
        return (currency == null || currency.isBlank()) ? "INR" : currency.toUpperCase();
    }

    private Long extractSuggestedCardId(RecommendationDTO recommendation) {
        if (recommendation == null) {
            return null;
        }
        if (recommendation.getBestCard() != null && recommendation.getBestCard().getId() != null) {
            return recommendation.getBestCard().getId();
        }
        if (recommendation.getBestOption() != null) {
            return recommendation.getBestOption().getCardId();
        }
        return null;
    }

    private BigDecimal estimatePotentialSavings(RecommendationDTO recommendation, Long actualCardId) {
        if (recommendation == null || actualCardId == null) {
            return null;
        }

        Double optimalRewardValue = recommendation.getBestCard() != null
                && recommendation.getBestCard().getRewards() != null
                ? recommendation.getBestCard().getRewards().getEstimatedValue()
                : null;
        if (optimalRewardValue == null) {
            return null;
        }

        Double actualRewardValue = null;
        if (recommendation.getBestCard() != null && actualCardId.equals(recommendation.getBestCard().getId())) {
            actualRewardValue = optimalRewardValue;
        } else if (recommendation.getComparisonTable() != null) {
            actualRewardValue = recommendation.getComparisonTable().stream()
                    .filter(row -> row != null && row.getCardId() != null && actualCardId.equals(row.getCardId()))
                    .map(RecommendationDTO.ComparisonRow::getEstimatedValue)
                    .filter(value -> value != null)
                    .findFirst()
                    .orElse(null);
        }

        if (actualRewardValue == null) {
            return null;
        }

        double delta = Math.max(0.0, optimalRewardValue - actualRewardValue);
        return BigDecimal.valueOf(delta).setScale(2, RoundingMode.HALF_UP);
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
}
