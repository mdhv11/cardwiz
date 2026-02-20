package com.cardwiz.userservice.models;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private BigDecimal amount;

    private String merchant;

    private String category;

    private String currency;

    private LocalDate transactionDate;

    private Long suggestedCardId;   // AI recommendation
    private Long actualCardId;      // What user selected
    private BigDecimal potentialSavings; // Additional rewards user could have earned by using suggested card

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
}
