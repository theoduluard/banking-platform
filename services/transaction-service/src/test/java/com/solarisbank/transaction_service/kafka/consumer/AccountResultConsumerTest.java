package com.solarisbank.transaction_service.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.solarisbank.transaction_service.client.AccountClient;
import com.solarisbank.transaction_service.kafka.event.CreditRequestedEvent;
import com.solarisbank.transaction_service.kafka.event.DebitResultEvent;
import com.solarisbank.transaction_service.kafka.event.CreditResultEvent;
import com.solarisbank.transaction_service.kafka.producer.SagaEventProducer;
import com.solarisbank.transaction_service.model.Transaction;
import com.solarisbank.transaction_service.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountResultConsumerTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private SagaEventProducer sagaEventProducer;

    @Mock
    private AccountClient accountClient;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @InjectMocks
    private AccountResultConsumer consumer;

    private UUID transactionId;
    private Transaction pendingTransaction;

    @BeforeEach
    void setUp() {
        transactionId = UUID.randomUUID();

        pendingTransaction = Transaction.builder()
                .id(transactionId)
                .fromAccountId(UUID.randomUUID())
                .toAccountId(UUID.randomUUID())
                .initiatedByUserId(UUID.randomUUID())
                .amount(new BigDecimal("150.00"))
                .currency("EUR")
                .type(Transaction.Type.TRANSFER)
                .status(Transaction.Status.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ── onDebitResult ──────────────────────────────────────────────────────────

    @Test
    void onDebitResult_shouldPublishCreditRequest_whenDebitSucceeds() throws Exception {
        String payload = objectMapper.writeValueAsString(
                new DebitResultEvent(transactionId, true, null));

        when(transactionRepository.findById(transactionId))
                .thenReturn(Optional.of(pendingTransaction));

        consumer.onDebitResult(payload);

        verify(sagaEventProducer).publishCreditRequest(any(CreditRequestedEvent.class));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void onDebitResult_shouldPublishCreditRequest_withCorrectFields() throws Exception {
        String payload = objectMapper.writeValueAsString(
                new DebitResultEvent(transactionId, true, null));

        when(transactionRepository.findById(transactionId))
                .thenReturn(Optional.of(pendingTransaction));

        consumer.onDebitResult(payload);

        verify(sagaEventProducer).publishCreditRequest(argThat(event ->
                event.getTransactionId().equals(transactionId)
                && event.getToAccountId().equals(pendingTransaction.getToAccountId())
                && event.getAmount().compareTo(new BigDecimal("150.00")) == 0
        ));
    }

    @Test
    void onDebitResult_shouldSetStatusFailed_whenDebitFails() throws Exception {
        String payload = objectMapper.writeValueAsString(
                new DebitResultEvent(transactionId, false, "Insufficient funds"));

        when(transactionRepository.findById(transactionId))
                .thenReturn(Optional.of(pendingTransaction));

        consumer.onDebitResult(payload);

        verify(transactionRepository).save(argThat(t ->
                t.getStatus() == Transaction.Status.FAILED));
        verifyNoInteractions(sagaEventProducer);
    }

    @Test
    void onDebitResult_shouldDoNothing_whenTransactionNotFound() throws Exception {
        String payload = objectMapper.writeValueAsString(
                new DebitResultEvent(transactionId, true, null));

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.empty());

        consumer.onDebitResult(payload);

        verifyNoInteractions(sagaEventProducer);
        verify(transactionRepository, never()).save(any());
    }

    // ── onCreditResult ─────────────────────────────────────────────────────────

    @Test
    void onCreditResult_shouldSetStatusCompleted_whenCreditSucceeds() throws Exception {
        String payload = objectMapper.writeValueAsString(
                new CreditResultEvent(transactionId, true, null));

        when(transactionRepository.findById(transactionId))
                .thenReturn(Optional.of(pendingTransaction));

        consumer.onCreditResult(payload);

        verify(transactionRepository).save(argThat(t ->
                t.getStatus() == Transaction.Status.COMPLETED
                && t.getCompletedAt() != null));
    }

    @Test
    void onCreditResult_shouldCompensateAndSetStatusFailed_whenCreditFails() throws Exception {
        String payload = objectMapper.writeValueAsString(
                new CreditResultEvent(transactionId, false, "Account blocked"));

        when(transactionRepository.findById(transactionId))
                .thenReturn(Optional.of(pendingTransaction));

        consumer.onCreditResult(payload);

        // Compensation : re-crédit du compte source
        verify(accountClient).credit(
                pendingTransaction.getFromAccountId(),
                pendingTransaction.getAmount());

        // Transaction marquée FAILED
        verify(transactionRepository).save(argThat(t ->
                t.getStatus() == Transaction.Status.FAILED));
    }
}
