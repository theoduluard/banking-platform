package com.solarisbank.notification_service.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.solarisbank.notification_service.kafka.event.TransactionCompletedEvent;
import com.solarisbank.notification_service.kafka.event.TransactionFailedEvent;
import com.solarisbank.notification_service.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionEventConsumerTest {

    @Mock
    private NotificationService notificationService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private TransactionEventConsumer consumer;

    private UUID transactionId;
    private UUID senderUserId;
    private UUID recipientUserId;

    @BeforeEach
    void setUp() {
        consumer       = new TransactionEventConsumer(notificationService, objectMapper);
        transactionId  = UUID.randomUUID();
        senderUserId   = UUID.randomUUID();
        recipientUserId = UUID.randomUUID();
    }

    // ── onTransactionCompleted ─────────────────────────────────────────────────

    @Test
    void onTransactionCompleted_validPayload_shouldDelegateToService() throws Exception {
        TransactionCompletedEvent event = TransactionCompletedEvent.builder()
                .transactionId(transactionId)
                .fromAccountId(UUID.randomUUID())
                .toAccountId(UUID.randomUUID())
                .senderUserId(senderUserId)
                .recipientUserId(recipientUserId)
                .amount(new BigDecimal("150.00"))
                .currency("EUR")
                .description("Rent payment")
                .completedAt(LocalDateTime.now())
                .build();

        String payload = objectMapper.writeValueAsString(event);
        consumer.onTransactionCompleted(payload);

        verify(notificationService).handleTransactionCompleted(any(TransactionCompletedEvent.class));
    }

    @Test
    void onTransactionCompleted_validPayload_shouldPassCorrectEvent() throws Exception {
        TransactionCompletedEvent event = TransactionCompletedEvent.builder()
                .transactionId(transactionId)
                .senderUserId(senderUserId)
                .recipientUserId(recipientUserId)
                .amount(new BigDecimal("500.00"))
                .currency("EUR")
                .completedAt(LocalDateTime.now())
                .build();

        String payload = objectMapper.writeValueAsString(event);
        consumer.onTransactionCompleted(payload);

        verify(notificationService).handleTransactionCompleted(
                argThat(e -> e.getTransactionId().equals(transactionId)
                        && e.getSenderUserId().equals(senderUserId)));
    }

    @Test
    void onTransactionCompleted_invalidJson_shouldNotThrowAndNotCallService() {
        consumer.onTransactionCompleted("{invalid json}");

        verify(notificationService, never()).handleTransactionCompleted(any());
    }

    @Test
    void onTransactionCompleted_emptyPayload_shouldNotThrowAndNotCallService() {
        consumer.onTransactionCompleted("");

        verify(notificationService, never()).handleTransactionCompleted(any());
    }

    @Test
    void onTransactionCompleted_nullFields_shouldStillDelegateToService() throws Exception {
        // Minimal event — no description, no recipient
        TransactionCompletedEvent event = TransactionCompletedEvent.builder()
                .transactionId(transactionId)
                .senderUserId(senderUserId)
                .amount(new BigDecimal("10.00"))
                .currency("EUR")
                .completedAt(LocalDateTime.now())
                .build();

        consumer.onTransactionCompleted(objectMapper.writeValueAsString(event));

        verify(notificationService).handleTransactionCompleted(any());
    }

    // ── onTransactionFailed ────────────────────────────────────────────────────

    @Test
    void onTransactionFailed_validPayload_shouldDelegateToService() throws Exception {
        TransactionFailedEvent event = TransactionFailedEvent.builder()
                .transactionId(transactionId)
                .senderUserId(senderUserId)
                .amount(new BigDecimal("200.00"))
                .currency("EUR")
                .reason("Insufficient funds")
                .failedAt(LocalDateTime.now())
                .build();

        String payload = objectMapper.writeValueAsString(event);
        consumer.onTransactionFailed(payload);

        verify(notificationService).handleTransactionFailed(any(TransactionFailedEvent.class));
    }

    @Test
    void onTransactionFailed_shouldPassCorrectTransactionId() throws Exception {
        TransactionFailedEvent event = TransactionFailedEvent.builder()
                .transactionId(transactionId)
                .senderUserId(senderUserId)
                .amount(new BigDecimal("75.00"))
                .currency("EUR")
                .failedAt(LocalDateTime.now())
                .build();

        consumer.onTransactionFailed(objectMapper.writeValueAsString(event));

        verify(notificationService).handleTransactionFailed(
                argThat(e -> e.getTransactionId().equals(transactionId)));
    }

    @Test
    void onTransactionFailed_invalidJson_shouldNotThrowAndNotCallService() {
        consumer.onTransactionFailed("not-json");

        verify(notificationService, never()).handleTransactionFailed(any());
    }

    @Test
    void onTransactionFailed_emptyPayload_shouldNotThrowAndNotCallService() {
        consumer.onTransactionFailed("");

        verify(notificationService, never()).handleTransactionFailed(any());
    }

    @Test
    void onTransactionFailed_noReason_shouldStillDelegateToService() throws Exception {
        TransactionFailedEvent event = TransactionFailedEvent.builder()
                .transactionId(transactionId)
                .senderUserId(senderUserId)
                .amount(new BigDecimal("50.00"))
                .currency("EUR")
                .failedAt(LocalDateTime.now())
                .build();

        consumer.onTransactionFailed(objectMapper.writeValueAsString(event));

        verify(notificationService).handleTransactionFailed(any());
    }
}
