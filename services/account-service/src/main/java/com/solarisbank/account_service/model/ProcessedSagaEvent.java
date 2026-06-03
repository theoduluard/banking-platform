package com.solarisbank.account_service.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Idempotency guard for Kafka saga events processed by account-service.
 * Before executing a debit or credit triggered by a Kafka message, the consumer
 * checks whether (transactionId, eventType) already exists in this table.
 * The UNIQUE constraint makes concurrent duplicate handling safe: if two threads
 * pass the exists-check simultaneously, only one INSERT will succeed — the other
 * will get a DataIntegrityViolationException which is caught and treated as a
 * duplicate.
 */
@Entity
@Table(
    name = "processed_saga_events",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_processed_saga_tx_type",
        columnNames = {"transaction_id", "event_type"}
    )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedSagaEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    /** "DEBIT" or "CREDIT" */
    @Column(name = "event_type", nullable = false, length = 20)
    private String eventType;

    @Column(nullable = false, updatable = false)
    private LocalDateTime processedAt;

    @PrePersist
    public void prePersist() {
        if (this.processedAt == null) this.processedAt = LocalDateTime.now();
    }
}
