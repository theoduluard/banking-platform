package com.solarisbank.notification_service.service;

import com.solarisbank.notification_service.exception.BusinessException;
import com.solarisbank.notification_service.kafka.event.TransactionCompletedEvent;
import com.solarisbank.notification_service.kafka.event.TransactionFailedEvent;
import com.solarisbank.notification_service.model.Notification;
import com.solarisbank.notification_service.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;

    // ── Kafka-driven creation ─────────────────────────────────────────────────

    /**
     * Creates two notifications for a completed transfer:
     * one for the sender ("Transfer sent") and one for the recipient ("Transfer received").
     * If sender == recipient (internal transfer) only one notification is created.
     */
    @Transactional
    public void handleTransactionCompleted(TransactionCompletedEvent event) {
        String amountFormatted = formatAmount(event.getAmount(), event.getCurrency());

        // Notify sender
        notificationRepository.save(Notification.builder()
                .userId(event.getSenderUserId())
                .type(Notification.Type.TRANSACTION_SENT)
                .title("Transfer sent")
                .message("Your transfer of " + amountFormatted
                        + (event.getDescription() != null && !event.getDescription().isBlank()
                                ? " — " + event.getDescription() : "")
                        + " has been completed.")
                .build());

        // Notify recipient (skip if it is the same user to avoid duplicates)
        if (event.getRecipientUserId() != null
                && !event.getRecipientUserId().equals(event.getSenderUserId())) {
            notificationRepository.save(Notification.builder()
                    .userId(event.getRecipientUserId())
                    .type(Notification.Type.TRANSACTION_RECEIVED)
                    .title("Transfer received")
                    .message("You received " + amountFormatted
                            + (event.getDescription() != null && !event.getDescription().isBlank()
                                    ? " — " + event.getDescription() : "")
                            + ".")
                    .build());
        }

        log.info("[Notification] Created COMPLETED notifications for transaction id={}",
                event.getTransactionId());
    }

    /**
     * Creates a failure notification for the sender only.
     */
    @Transactional
    public void handleTransactionFailed(TransactionFailedEvent event) {
        String amountFormatted = formatAmount(event.getAmount(), event.getCurrency());

        notificationRepository.save(Notification.builder()
                .userId(event.getSenderUserId())
                .type(Notification.Type.TRANSACTION_FAILED)
                .title("Transfer failed")
                .message("Your transfer of " + amountFormatted + " could not be completed."
                        + (event.getReason() != null && !event.getReason().isBlank()
                                ? " Reason: " + event.getReason() : ""))
                .build());

        log.info("[Notification] Created FAILED notification for transaction id={}",
                event.getTransactionId());
    }

    // ── REST-driven queries and updates ───────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<Notification> getNotifications(UUID userId, int page, int size) {
        return notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public Notification markRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository
                .findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new BusinessException("Notification not found", HttpStatus.NOT_FOUND));
        notification.setRead(true);
        return notificationRepository.save(notification);
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return notificationRepository.markAllReadByUserId(userId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String formatAmount(java.math.BigDecimal amount, String currency) {
        return amount.stripTrailingZeros().toPlainString() + " " + (currency != null ? currency : "EUR");
    }
}
