package com.cardwiz.userservice.services;

import com.cardwiz.userservice.customExceptions.UserNotFoundException;
import com.cardwiz.userservice.dtos.AdvisorMessageCreateRequest;
import com.cardwiz.userservice.dtos.AdvisorMessageResponse;
import com.cardwiz.userservice.models.AdvisorMessage;
import com.cardwiz.userservice.models.User;
import com.cardwiz.userservice.repositories.AdvisorMessageRepository;
import com.cardwiz.userservice.repositories.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdvisorHistoryService {

    private static final Set<String> ALLOWED_SENDERS = Set.of("user", "bot");

    private final AdvisorMessageRepository advisorMessageRepository;
    private final UserRepository userRepository;

    public List<AdvisorMessageResponse> getHistory(Long userId) {
        return advisorMessageRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AdvisorMessageResponse saveMessage(Long userId, AdvisorMessageCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        String sender = normalizeSender(request.getSender());
        String text = request.getText() == null ? "" : request.getText().trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Message text cannot be empty");
        }

        AdvisorMessage message = advisorMessageRepository.save(
                AdvisorMessage.builder()
                        .user(user)
                        .sender(sender)
                        .text(text)
                        .build()
        );

        return toResponse(message);
    }

    @Transactional
    public void clearHistory(Long userId) {
        advisorMessageRepository.deleteByUserId(userId);
    }

    private String normalizeSender(String rawSender) {
        String sender = rawSender == null ? "" : rawSender.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SENDERS.contains(sender)) {
            throw new IllegalArgumentException("Invalid sender. Allowed values: user, bot");
        }
        return sender;
    }

    private AdvisorMessageResponse toResponse(AdvisorMessage message) {
        return AdvisorMessageResponse.builder()
                .id(message.getId())
                .sender(message.getSender())
                .text(message.getText())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
