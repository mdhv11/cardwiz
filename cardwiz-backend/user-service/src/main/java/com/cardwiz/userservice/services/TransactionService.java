package com.cardwiz.userservice.services;

import com.cardwiz.userservice.customExceptions.UserNotFoundException;
import com.cardwiz.userservice.dtos.TransactionRequest;
import com.cardwiz.userservice.dtos.TransactionResponse;
import com.cardwiz.userservice.models.Transaction;
import com.cardwiz.userservice.models.User;
import com.cardwiz.userservice.repositories.TransactionRepository;
import com.cardwiz.userservice.repositories.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public List<TransactionResponse> listTransactions(Long userId) {
        return transactionRepository.findByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public TransactionResponse getTransaction(Long userId, Long transactionId) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
        if (!tx.getUser().getId().equals(userId)) {
            throw new RuntimeException("Transaction does not belong to user");
        }
        return toResponse(tx);
    }

    @Transactional
    @CacheEvict(cacheNames = "aiRecommendationsV2", allEntries = true)
    public TransactionResponse createTransaction(Long userId, TransactionRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        Transaction tx = Transaction.builder()
                .amount(request.getAmount())
                .merchant(request.getMerchant())
                .category(request.getCategory())
                .transactionDate(request.getTransactionDate())
                .suggestedCardId(request.getSuggestedCardId())
                .actualCardId(request.getActualCardId())
                .user(user)
                .build();

        return toResponse(transactionRepository.save(tx));
    }

    @Transactional
    @CacheEvict(cacheNames = "aiRecommendationsV2", allEntries = true)
    public TransactionResponse updateTransaction(Long userId, Long transactionId, TransactionRequest request) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
        if (!tx.getUser().getId().equals(userId)) {
            throw new RuntimeException("Transaction does not belong to user");
        }

        if (request.getAmount() != null) {
            tx.setAmount(request.getAmount());
        }
        if (request.getMerchant() != null) {
            tx.setMerchant(request.getMerchant());
        }
        if (request.getCategory() != null) {
            tx.setCategory(request.getCategory());
        }
        if (request.getTransactionDate() != null) {
            tx.setTransactionDate(request.getTransactionDate());
        }
        if (request.getSuggestedCardId() != null) {
            tx.setSuggestedCardId(request.getSuggestedCardId());
        }
        if (request.getActualCardId() != null) {
            tx.setActualCardId(request.getActualCardId());
        }

        return toResponse(transactionRepository.save(tx));
    }

    @Transactional
    @CacheEvict(cacheNames = "aiRecommendationsV2", allEntries = true)
    public void deleteTransaction(Long userId, Long transactionId) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
        if (!tx.getUser().getId().equals(userId)) {
            throw new RuntimeException("Transaction does not belong to user");
        }
        transactionRepository.delete(tx);
    }

    private TransactionResponse toResponse(Transaction tx) {
        return TransactionResponse.builder()
                .id(tx.getId())
                .amount(tx.getAmount())
                .merchant(tx.getMerchant())
                .category(tx.getCategory())
                .transactionDate(tx.getTransactionDate())
                .suggestedCardId(tx.getSuggestedCardId())
                .actualCardId(tx.getActualCardId())
                .build();
    }
}
