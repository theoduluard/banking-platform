package com.solarisbank.card_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cards")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "card_number", nullable = false, unique = true)
    private String cardNumber;

    @Column(name = "masked_number", nullable = false)
    private String maskedNumber;

    @Column(name = "cardholder_name", nullable = false)
    private String cardholderName;

    @Column(name = "card_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private CardType cardType;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private CardStatus status;

    @Column(name = "expiry_month", nullable = false)
    private short expiryMonth;

    @Column(name = "expiry_year", nullable = false)
    private short expiryYear;

    @Column(name = "cvv_hash")
    private String cvvHash;

    @Column(name = "spending_limit")
    private BigDecimal spendingLimit;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum CardType { VIRTUAL, PHYSICAL }
    public enum CardStatus { ACTIVE, FROZEN, CANCELLED }
}
