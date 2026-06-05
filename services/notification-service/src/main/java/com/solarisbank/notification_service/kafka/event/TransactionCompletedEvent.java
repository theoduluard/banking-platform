package com.solarisbank.notification_service.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published by transaction-service on topic "transaction.completed"
 * when the debit-credit saga completes successfully.
 *
 * <p>Both {@code senderUserId} and {@code recipientUserId} are included so that
 * the notification-service can fan-out to both parties without querying any other service.
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
    /** Owner of the destination account (looked up from account-service by transaction-service). */
    private UUID recipientUserId;
    private BigDecimal amount;
    private String currency;
    private String description;
    private LocalDateTime completedAt;
}
