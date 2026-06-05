package com.solarisbank.transaction_service.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.solarisbank.transaction_service.client.AccountClient;
import com.solarisbank.transaction_service.client.dto.AccountResponse;
import com.solarisbank.transaction_service.kafka.event.CreditRequestedEvent;
import com.solarisbank.transaction_service.kafka.event.DebitResultEvent;
import com.solarisbank.transaction_service.kafka.event.CreditResultEvent;
import com.solarisbank.transaction_service.kafka.event.TransactionCompletedEvent;
import com.solarisbank.transaction_service.kafka.event.TransactionFailedEvent;
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
    // Used by onCreditResult tests: the consumer guard requires DEBIT_CONFIRMED
    private Transaction debitConfirmedTransaction;

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

        // Same data but already past the DEBIT_CONFIRMED transition
        debitConfirmedTransaction = Transaction.builder()
                .id(transactionId)
                .fromAccountId(pendingTransaction.getFromAccountId())
                .toAccountId(pendingTransaction.getToAccountId())
                .initiatedByUserId(pendingTransaction.getInitiatedByUserId())
                .amount(new BigDecimal("150.00"))
                .currency("EUR")
                .type(Transaction.Type.TRANSFER)
                .status(Transaction.Status.DEBIT_CONFIRMED)
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
        verify(transactionRepository).save(argThat(t ->
                t.getStatus() == Transaction.Status.DEBIT_CONFIRMED));
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
        // Sender must be notified that the transfer failed
        verify(sagaEventProducer).publishTransactionFailed(argThat(e ->
                e.getTransactionId().equals(transactionId)
                && e.getSenderUserId().equals(pendingTransaction.getInitiatedByUserId())
                && "Insufficient funds".equals(e.getReason())));
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
                .thenReturn(Optional.of(debitConfirmedTransaction));

        // Stub internal account lookup used for recipient userId resolution
        AccountResponse recipientAccount = new AccountResponse();
        recipientAccount.setUserId(UUID.randomUUID());
        when(accountClient.getAccountInternal(debitConfirmedTransaction.getToAccountId()))
                .thenReturn(recipientAccount);

        consumer.onCreditResult(payload);

        verify(transactionRepository).save(argThat(t ->
                t.getStatus() == Transaction.Status.COMPLETED
                && t.getCompletedAt() != null));
        // Notification event must be published for both parties
        verify(sagaEventProducer).publishTransactionCompleted(argThat(e ->
                e.getTransactionId().equals(transactionId)
                && e.getSenderUserId().equals(debitConfirmedTransaction.getInitiatedByUserId())
                && e.getRecipientUserId().equals(recipientAccount.getUserId())));
    }

    @Test
    void onCreditResult_shouldCompensateAndSetStatusFailed_whenCreditFails() throws Exception {
        String payload = objectMapper.writeValueAsString(
                new CreditResultEvent(transactionId, false, "Account blocked"));

        when(transactionRepository.findById(transactionId))
                .thenReturn(Optional.of(debitConfirmedTransaction));

        consumer.onCreditResult(payload);

        // Compensation : re-crédit du compte source
        verify(accountClient).credit(
                debitConfirmedTransaction.getFromAccountId(),
                debitConfirmedTransaction.getAmount());

        // Transaction marquée FAILED
        verify(transactionRepository).save(argThat(t ->
                t.getStatus() == Transaction.Status.FAILED));

        // Sender must be notified that the transfer failed
        verify(sagaEventProducer).publishTransactionFailed(argThat(e ->
                e.getTransactionId().equals(transactionId)
                && "Account blocked".equals(e.getReason())));
    }

    // ── Poison-pill / invalid JSON ─────────────────────────────────────────────

    @Test
    void onDebitResult_shouldSkipSilently_whenPayloadIsInvalidJson() {
        // Must not propagate — just log and return
        consumer.onDebitResult("{ not valid json }");

        verifyNoInteractions(transactionRepository, sagaEventProducer, accountClient);
    }

    @Test
    void onCreditResult_shouldSkipSilently_whenPayloadIsInvalidJson() {
        consumer.onCreditResult("{ not valid json }");

        verifyNoInteractions(transactionRepository, accountClient);
    }

    // ── Idempotency guards ─────────────────────────────────────────────────────

    @Test
    void onDebitResult_shouldSkip_whenTransactionStatusIsNotPending() throws Exception {
        // Redelivered DebitResult: transaction already advanced past PENDING
        Transaction completedTx = Transaction.builder()
                .id(transactionId)
                .fromAccountId(UUID.randomUUID())
                .toAccountId(UUID.randomUUID())
                .initiatedByUserId(UUID.randomUUID())
                .amount(new BigDecimal("150.00"))
                .currency("EUR")
                .type(Transaction.Type.TRANSFER)
                .status(Transaction.Status.COMPLETED)
                .createdAt(LocalDateTime.now())
                .build();

        String payload = objectMapper.writeValueAsString(
                new DebitResultEvent(transactionId, true, null));

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(completedTx));

        consumer.onDebitResult(payload);

        // Idempotency guard must prevent any state change or Kafka publish
        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(sagaEventProducer);
    }

    @Test
    void onCreditResult_shouldSkip_whenTransactionNotFound() throws Exception {
        String payload = objectMapper.writeValueAsString(
                new CreditResultEvent(transactionId, true, null));

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.empty());

        consumer.onCreditResult(payload);

        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(accountClient);
    }

    @Test
    void onCreditResult_shouldSkip_whenTransactionStatusIsNotDebitConfirmed() throws Exception {
        // Redelivered CreditResult: transaction is already COMPLETED
        Transaction completedTx = Transaction.builder()
                .id(transactionId)
                .fromAccountId(UUID.randomUUID())
                .toAccountId(UUID.randomUUID())
                .initiatedByUserId(UUID.randomUUID())
                .amount(new BigDecimal("150.00"))
                .currency("EUR")
                .type(Transaction.Type.TRANSFER)
                .status(Transaction.Status.COMPLETED)
                .createdAt(LocalDateTime.now())
                .build();

        String payload = objectMapper.writeValueAsString(
                new CreditResultEvent(transactionId, true, null));

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(completedTx));

        consumer.onCreditResult(payload);

        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(accountClient);
    }

    // ── Compensation failure ───────────────────────────────────────────────────

    @Test
    void onCreditResult_shouldLogCriticalError_whenCompensationFails() throws Exception {
        // Credit FAILED → compensation REST call also throws; must not propagate
        String payload = objectMapper.writeValueAsString(
                new CreditResultEvent(transactionId, false, "Account blocked"));

        when(transactionRepository.findById(transactionId))
                .thenReturn(Optional.of(debitConfirmedTransaction));
        doThrow(new RuntimeException("account-service unavailable"))
                .when(accountClient).credit(any(), any());

        // Must not throw — error is swallowed and logged as CRITICAL
        consumer.onCreditResult(payload);

        // Transaction is still marked FAILED despite the compensation error
        verify(transactionRepository).save(argThat(t ->
                t.getStatus() == Transaction.Status.FAILED));
    }
}
