package com.solarisbank.notification_service.service;

import com.solarisbank.notification_service.exception.BusinessException;
import com.solarisbank.notification_service.kafka.event.TransactionCompletedEvent;
import com.solarisbank.notification_service.kafka.event.TransactionFailedEvent;
import com.solarisbank.notification_service.model.Notification;
import com.solarisbank.notification_service.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    private UUID senderId;
    private UUID recipientId;
    private UUID transactionId;

    @BeforeEach
    void setUp() {
        senderId      = UUID.randomUUID();
        recipientId   = UUID.randomUUID();
        transactionId = UUID.randomUUID();
        // lenient: only the event-handler tests call save(); the query tests don't
        lenient().when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ── handleTransactionCompleted ─────────────────────────────────────────────

    @Test
    void handleTransactionCompleted_shouldCreateTwoNotifications_forDifferentUsers() {
        TransactionCompletedEvent event = TransactionCompletedEvent.builder()
                .transactionId(transactionId)
                .fromAccountId(UUID.randomUUID())
                .toAccountId(UUID.randomUUID())
                .senderUserId(senderId)
                .recipientUserId(recipientId)
                .amount(new BigDecimal("250.00"))
                .currency("EUR")
                .description("Rent")
                .completedAt(LocalDateTime.now())
                .build();

        notificationService.handleTransactionCompleted(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());

        List<Notification> saved = captor.getAllValues();
        Notification senderNotif = saved.stream()
                .filter(n -> n.getType() == Notification.Type.TRANSACTION_SENT)
                .findFirst().orElseThrow();
        Notification recipientNotif = saved.stream()
                .filter(n -> n.getType() == Notification.Type.TRANSACTION_RECEIVED)
                .findFirst().orElseThrow();

        assertThat(senderNotif.getUserId()).isEqualTo(senderId);
        assertThat(senderNotif.getMessage()).contains("250").contains("EUR").contains("Rent");
        assertThat(recipientNotif.getUserId()).isEqualTo(recipientId);
        assertThat(recipientNotif.getMessage()).contains("250").contains("EUR").contains("Rent");
    }

    @Test
    void handleTransactionCompleted_shouldCreateOneNotification_whenSenderEqualsRecipient() {
        TransactionCompletedEvent event = TransactionCompletedEvent.builder()
                .transactionId(transactionId)
                .senderUserId(senderId)
                .recipientUserId(senderId)   // same user
                .amount(new BigDecimal("50.00"))
                .currency("EUR")
                .completedAt(LocalDateTime.now())
                .build();

        notificationService.handleTransactionCompleted(event);

        verify(notificationRepository, times(1)).save(any());
    }

    @Test
    void handleTransactionCompleted_shouldCreateOneNotification_whenRecipientIsNull() {
        TransactionCompletedEvent event = TransactionCompletedEvent.builder()
                .transactionId(transactionId)
                .senderUserId(senderId)
                .recipientUserId(null)
                .amount(new BigDecimal("100.00"))
                .currency("EUR")
                .completedAt(LocalDateTime.now())
                .build();

        notificationService.handleTransactionCompleted(event);

        verify(notificationRepository, times(1)).save(any());
    }

    @Test
    void handleTransactionCompleted_shouldOmitDescription_whenBlank() {
        TransactionCompletedEvent event = TransactionCompletedEvent.builder()
                .transactionId(transactionId)
                .senderUserId(senderId)
                .recipientUserId(null)
                .amount(new BigDecimal("10.00"))
                .currency("EUR")
                .description("")
                .completedAt(LocalDateTime.now())
                .build();

        notificationService.handleTransactionCompleted(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getMessage()).doesNotContain("—");
    }

    // ── handleTransactionFailed ────────────────────────────────────────────────

    @Test
    void handleTransactionFailed_shouldCreateFailedNotification_forSender() {
        TransactionFailedEvent event = TransactionFailedEvent.builder()
                .transactionId(transactionId)
                .senderUserId(senderId)
                .amount(new BigDecimal("75.50"))
                .currency("EUR")
                .reason("Insufficient funds")
                .failedAt(LocalDateTime.now())
                .build();

        notificationService.handleTransactionFailed(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());

        Notification n = captor.getValue();
        assertThat(n.getUserId()).isEqualTo(senderId);
        assertThat(n.getType()).isEqualTo(Notification.Type.TRANSACTION_FAILED);
        assertThat(n.getMessage()).contains("75.5").contains("EUR").contains("Insufficient funds");
    }

    @Test
    void handleTransactionFailed_shouldOmitReason_whenBlank() {
        TransactionFailedEvent event = TransactionFailedEvent.builder()
                .transactionId(transactionId)
                .senderUserId(senderId)
                .amount(new BigDecimal("10.00"))
                .currency("EUR")
                .reason("")
                .failedAt(LocalDateTime.now())
                .build();

        notificationService.handleTransactionFailed(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getMessage()).doesNotContain("Reason:");
    }

    // ── getNotifications ───────────────────────────────────────────────────────

    @Test
    void getNotifications_shouldReturnPageForUser() {
        Notification n = Notification.builder()
                .id(UUID.randomUUID()).userId(senderId)
                .type(Notification.Type.TRANSACTION_SENT)
                .title("T").message("M").build();

        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(senderId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(n)));

        Page<Notification> result = notificationService.getNotifications(senderId, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUserId()).isEqualTo(senderId);
    }

    // ── countUnread ────────────────────────────────────────────────────────────

    @Test
    void countUnread_shouldReturnCount() {
        when(notificationRepository.countByUserIdAndReadFalse(senderId)).thenReturn(3L);
        assertThat(notificationService.countUnread(senderId)).isEqualTo(3L);
    }

    // ── markRead ───────────────────────────────────────────────────────────────

    @Test
    void markRead_shouldSetReadTrue() {
        UUID notifId = UUID.randomUUID();
        Notification n = Notification.builder()
                .id(notifId).userId(senderId)
                .type(Notification.Type.TRANSACTION_SENT)
                .title("T").message("M").read(false).build();
        when(notificationRepository.findByIdAndUserId(notifId, senderId)).thenReturn(Optional.of(n));

        Notification updated = notificationService.markRead(notifId, senderId);

        assertThat(updated.isRead()).isTrue();
        verify(notificationRepository).save(n);
    }

    @Test
    void markRead_shouldThrow404_whenNotificationNotFound() {
        UUID notifId = UUID.randomUUID();
        when(notificationRepository.findByIdAndUserId(notifId, senderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(notifId, senderId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Notification not found");
    }

    // ── markAllRead ────────────────────────────────────────────────────────────

    @Test
    void markAllRead_shouldCallRepository() {
        when(notificationRepository.markAllReadByUserId(senderId)).thenReturn(5);
        assertThat(notificationService.markAllRead(senderId)).isEqualTo(5);
        verify(notificationRepository).markAllReadByUserId(senderId);
    }
}
