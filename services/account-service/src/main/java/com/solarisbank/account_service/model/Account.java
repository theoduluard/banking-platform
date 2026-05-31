package com.solarisbank.account_service.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "accounts")
public class Account {
    public enum Type { CHECKING, SAVINGS }
    public enum Status { PENDING_APPROVAL, ACTIVE, BLOCKED, CLOSED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID accountId;

    @Column(nullable = false)
    private UUID userId;

    @Column(unique = true, nullable = false, length = 34)
    private String iban;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.balance = BigDecimal.ZERO;
        this.status = Status.PENDING_APPROVAL;
        this.currency = "EUR";
    }
}
