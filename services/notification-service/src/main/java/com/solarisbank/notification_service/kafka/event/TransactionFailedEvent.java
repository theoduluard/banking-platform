package com.solarisbank.notification_service.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published by transaction-service on topic "transaction.failed"
 * when the saga fails at the debit or credit step.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionFailedEvent {
    private UUID transactionId;
    private UUID fromAccountId;
    private UUID toAccountId;
    /** User who initiated the transfer — the only person notified on failure. */
    private UUID senderUserId;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String reason;
    private LocalDateTime failedAt;
}
