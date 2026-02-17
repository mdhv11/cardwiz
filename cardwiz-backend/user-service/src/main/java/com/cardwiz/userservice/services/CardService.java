package com.cardwiz.userservice.services;

import com.cardwiz.userservice.customExceptions.UserNotFoundException;
import com.cardwiz.userservice.dtos.UserCardRequest;
import com.cardwiz.userservice.dtos.UserCardResponse;
import com.cardwiz.userservice.models.ProcessingStatus;
import com.cardwiz.userservice.models.UploadedDocument;
import com.cardwiz.userservice.models.User;
import com.cardwiz.userservice.models.UserCard;
import com.cardwiz.userservice.repositories.UploadedDocumentRepository;
import com.cardwiz.userservice.repositories.UserCardRepository;
import com.cardwiz.userservice.repositories.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CardService {

    private final UserCardRepository userCardRepository;
    private final UserRepository userRepository;
    private final UploadedDocumentRepository uploadedDocumentRepository;

    @Cacheable(cacheNames = "cardMetadataByUser", key = "#userId")
    public List<UserCardResponse> getCardsForUser(Long userId) {
        return userCardRepository.findByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Cacheable(cacheNames = "cardMetadataById", key = "T(java.lang.String).valueOf(#userId).concat(':').concat(T(java.lang.String).valueOf(#cardId))")
    public UserCardResponse getCard(Long userId, Long cardId) {
        UserCard card = userCardRepository.findById(cardId)
                .orElseThrow(() -> new RuntimeException("Card not found"));
        if (!card.getUser().getId().equals(userId)) {
            throw new RuntimeException("Card does not belong to user");
        }
        return toResponse(card);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = {"cardMetadataByUser", "cardMetadataById"}, allEntries = true),
            @CacheEvict(cacheNames = "aiRecommendations", allEntries = true)
    })
    public UserCardResponse createCard(Long userId, UserCardRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        UserCard card = UserCard.builder()
                .cardName(request.getCardName())
                .issuer(request.getIssuer())
                .network(request.getNetwork())
                .lastFourDigits(request.getLastFourDigits())
                .active(request.isActive())
                .user(user)
                .build();

        return toResponse(userCardRepository.save(card));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = {"cardMetadataByUser", "cardMetadataById"}, allEntries = true),
            @CacheEvict(cacheNames = "aiRecommendations", allEntries = true)
    })
    public UserCardResponse updateCard(Long userId, Long cardId, UserCardRequest request) {
        UserCard card = userCardRepository.findById(cardId)
                .orElseThrow(() -> new RuntimeException("Card not found"));
        if (!card.getUser().getId().equals(userId)) {
            throw new RuntimeException("Card does not belong to user");
        }

        if (request.getCardName() != null) {
            card.setCardName(request.getCardName());
        }
        if (request.getIssuer() != null) {
            card.setIssuer(request.getIssuer());
        }
        if (request.getNetwork() != null) {
            card.setNetwork(request.getNetwork());
        }
        if (request.getLastFourDigits() != null) {
            card.setLastFourDigits(request.getLastFourDigits());
        }
        card.setActive(request.isActive());

        return toResponse(userCardRepository.save(card));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = {"cardMetadataByUser", "cardMetadataById"}, allEntries = true),
            @CacheEvict(cacheNames = "aiRecommendations", allEntries = true)
    })
    public void deleteCard(Long userId, Long cardId) {
        UserCard card = userCardRepository.findById(cardId)
                .orElseThrow(() -> new RuntimeException("Card not found"));
        if (!card.getUser().getId().equals(userId)) {
            throw new RuntimeException("Card does not belong to user");
        }
        userCardRepository.delete(card);
    }

    public List<Long> getActiveCardIds(Long userId) {
        return userCardRepository.findByUserId(userId).stream()
                .filter(UserCard::isActive)
                .map(UserCard::getId)
                .toList();
    }

    @Transactional
    public UploadedDocument createDocumentRecord(Long userId, String s3Key, String documentType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        UploadedDocument document = UploadedDocument.builder()
                .s3Url(s3Key)
                .documentType(documentType)
                .status(ProcessingStatus.PENDING)
                .user(user)
                .build();
        return uploadedDocumentRepository.save(document);
    }

    @Transactional
    public UploadedDocument markDocumentComplete(Long documentId, String aiSummary) {
        UploadedDocument document = uploadedDocumentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document record not found"));
        document.setStatus(ProcessingStatus.COMPLETED);
        document.setAiSummary(aiSummary);
        return uploadedDocumentRepository.save(document);
    }

    @Transactional
    public UploadedDocument markDocumentFailed(Long documentId) {
        UploadedDocument document = uploadedDocumentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document record not found"));
        document.setStatus(ProcessingStatus.FAILED);
        return uploadedDocumentRepository.save(document);
    }

    private UserCardResponse toResponse(UserCard card) {
        return UserCardResponse.builder()
                .id(card.getId())
                .cardName(card.getCardName())
                .issuer(card.getIssuer())
                .network(card.getNetwork())
                .lastFourDigits(card.getLastFourDigits())
                .active(card.isActive())
                .build();
    }
}
