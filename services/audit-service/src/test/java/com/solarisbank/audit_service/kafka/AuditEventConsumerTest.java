package com.solarisbank.audit_service.kafka;

import com.solarisbank.audit_service.model.AuditEvent;
import com.solarisbank.audit_service.repository.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditEventConsumerTest {

    @Mock
    private AuditEventRepository repo;

    private AuditEventConsumer consumer;

    private final UUID USER_ID = UUID.randomUUID();
    private final UUID TX_ID   = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        consumer = new AuditEventConsumer(repo);
    }

    // ── consumeTransactions ────────────────────────────────────────────────────

    @Test
    void consumeTransactions_validMessage_shouldSaveWithTransactionSource() {
        String message = """
                {"type":"TRANSFER_COMPLETED","userId":"%s","transactionId":"%s"}
                """.formatted(USER_ID, TX_ID).strip();
        when(repo.save(any(AuditEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.consumeTransactions(message);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repo).save(captor.capture());
        AuditEvent event = captor.getValue();

        assertThat(event.getSource()).isEqualTo("transaction-service");
        assertThat(event.getEntityType()).isEqualTo("TRANSACTION");
        assertThat(event.getEventType()).isEqualTo("TRANSFER_COMPLETED");
        assertThat(event.getUserId()).isEqualTo(USER_ID);
        assertThat(event.getEntityId()).isEqualTo(TX_ID);
        assertThat(event.getPayload()).isEqualTo(message);
        assertThat(event.getCreatedAt()).isNotNull();
    }

    // ── consumeAuthEvents ──────────────────────────────────────────────────────

    @Test
    void consumeAuthEvents_validMessage_shouldSaveWithAuthSource() {
        String message = """
                {"type":"LOGIN","userId":"%s","id":"%s"}
                """.formatted(USER_ID, TX_ID).strip();
        when(repo.save(any(AuditEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.consumeAuthEvents(message);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repo).save(captor.capture());
        AuditEvent event = captor.getValue();

        assertThat(event.getSource()).isEqualTo("auth-service");
        assertThat(event.getEntityType()).isEqualTo("AUTH");
        assertThat(event.getEventType()).isEqualTo("LOGIN");
        assertThat(event.getEntityId()).isEqualTo(TX_ID);
    }

    // ── Missing fields ─────────────────────────────────────────────────────────

    @Test
    void consume_missingUserId_shouldSaveWithNullUserId() {
        String message = """
                {"type":"TRANSFER_COMPLETED","transactionId":"%s"}
                """.formatted(TX_ID).strip();
        when(repo.save(any(AuditEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.consumeTransactions(message);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isNull();
    }

    @Test
    void consume_missingType_defaultsToUnknown() {
        String message = """
                {"userId":"%s"}
                """.formatted(USER_ID).strip();
        when(repo.save(any(AuditEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.consumeTransactions(message);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("UNKNOWN");
    }

    @Test
    void consume_invalidEntityId_shouldSaveWithNullEntityId() {
        String message = """
                {"type":"LOGIN","userId":"%s","transactionId":"not-a-uuid"}
                """.formatted(USER_ID).strip();
        when(repo.save(any(AuditEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.consumeTransactions(message);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getEntityId()).isNull();
    }

    // ── Error handling ─────────────────────────────────────────────────────────

    @Test
    void consume_invalidJson_shouldNotThrowAndNotSave() {
        consumer.consumeTransactions("{invalid json}");
        verify(repo, never()).save(any());
    }

    @Test
    void consume_emptyMessage_shouldNotThrow() {
        consumer.consumeAuthEvents("");
        verify(repo, never()).save(any());
    }

    @Test
    void consume_entityIdFallsBackToTransactionId() {
        // When "id" is absent, should use "transactionId"
        String message = """
                {"type":"DEBIT","userId":"%s","transactionId":"%s"}
                """.formatted(USER_ID, TX_ID).strip();
        when(repo.save(any(AuditEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.consumeTransactions(message);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getEntityId()).isEqualTo(TX_ID);
    }

    @Test
    void consumeTransactions_senderUserIdFallback_shouldBeUsedWhenUserIdAbsent() {
        // Real TransactionCompletedEvent uses "senderUserId" instead of "userId"
        String message = """
                {"transactionId":"%s","senderUserId":"%s","fromAccountId":"00000000-0000-0000-0000-000000000001","amount":"500"}
                """.formatted(TX_ID, USER_ID).strip();
        when(repo.save(any(AuditEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.consumeTransactions(message);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repo).save(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.getUserId()).isEqualTo(USER_ID);
        assertThat(event.getEntityId()).isEqualTo(TX_ID);
        assertThat(event.getSource()).isEqualTo("transaction-service");
        assertThat(event.getEntityType()).isEqualTo("TRANSACTION");
    }
}
