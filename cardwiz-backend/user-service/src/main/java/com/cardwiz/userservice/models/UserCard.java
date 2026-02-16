package com.cardwiz.userservice.models;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_cards")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String cardName;       // e.g. "Flipkart Axis Bank"
    private String issuer;         // e.g. "Axis Bank"
    private String network;        // VISA / Mastercard

    @Column(length = 4)
    private String lastFourDigits;

    private boolean active = true;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
}
