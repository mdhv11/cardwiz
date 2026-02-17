package com.cardwiz.userservice.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "user_cards")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // Links to your existing User entity

    private String cardName;
    private String issuer;
    private String network;

    private String lastFourDigits;
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    private DocumentStatus docStatus = DocumentStatus.NOT_UPLOADED;

    private String docS3Key;
    private Instant lastAnalyzedAt;
}
