package com.solarisbank.transaction_service.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published on topic "transaction.completed" when the debit-credit saga succeeds.
 * Consumed by notification-service to fan out notifications to both parties.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionCompletedEvent {
    private UUID transactionId;
    private UUID fromAccountId;
    private UUID toAccountId;
    /** User who initiated the transfer. */
    private UUID senderUserId;
    /** Owner of the destination account — resolved from account-service at saga completion. */
    private UUID recipientUserId;
    private BigDecimal amount;
    private String currency;
    private String description;
    private LocalDateTime completedAt;
}
