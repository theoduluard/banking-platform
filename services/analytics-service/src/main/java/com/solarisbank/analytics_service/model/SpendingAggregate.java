package com.solarisbank.analytics_service.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "spending_aggregates")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SpendingAggregate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private short year;

    @Column(nullable = false)
    private short month;

    @Column(nullable = false)
    private String category;

    @Column(name = "total_debit", nullable = false)
    private BigDecimal totalDebit;

    @Column(name = "total_credit", nullable = false)
    private BigDecimal totalCredit;

    @Column(name = "tx_count", nullable = false)
    private int txCount;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
