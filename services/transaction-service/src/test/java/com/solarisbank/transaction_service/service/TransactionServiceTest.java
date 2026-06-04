package com.solarisbank.transaction_service.service;

import com.solarisbank.transaction_service.client.AccountClient;
import com.solarisbank.transaction_service.client.dto.AccountResponse;
import com.solarisbank.transaction_service.dto.TransactionResponse;
import com.solarisbank.transaction_service.dto.TransferRequest;
import com.solarisbank.transaction_service.exception.BusinessException;
import com.solarisbank.transaction_service.kafka.event.DebitRequestedEvent;
import com.solarisbank.transaction_service.kafka.producer.SagaEventProducer;
import com.solarisbank.transaction_service.model.Transaction;
import com.solarisbank.transaction_service.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountClient accountClient;

    @Mock
    private SagaEventProducer sagaEventProducer;

    @InjectMocks
    private TransactionService transactionService;

    private UUID userId;
    private UUID fromAccountId;
    private UUID toAccountId;
    private UUID transactionId;
    private TransferRequest transferRequest;
    private AccountResponse activeSourceAccount;
    private Transaction pendingTransaction;

    @BeforeEach
    void setUp() {
        userId        = UUID.randomUUID();
        fromAccountId = UUID.randomUUID();
        toAccountId   = UUID.randomUUID();
        transactionId = UUID.randomUUID();

        transferRequest = new TransferRequest();
        transferRequest.setFromAccountId(fromAccountId);
        transferRequest.setToAccountId(toAccountId);
        transferRequest.setAmount(new BigDecimal("100.00"));
        transferRequest.setDescription("Test transfer");

        activeSourceAccount = new AccountResponse();
        activeSourceAccount.setId(fromAccountId);
        activeSourceAccount.setBalance(new BigDecimal("500.00"));
        activeSourceAccount.setStatus("ACTIVE");
        activeSourceAccount.setUserId(userId);

        pendingTransaction = Transaction.builder()
                .id(transactionId)
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .initiatedByUserId(userId)
                .amount(new BigDecimal("100.00"))
                .currency("EUR")
                .type(Transaction.Type.TRANSFER)
                .status(Transaction.Status.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ── transfer ───────────────────────────────────────────────────────────────

    @Test
    void transfer_shouldReturnPendingTransaction_andPublishDebitEvent() {
        when(accountClient.getAccount(fromAccountId, userId)).thenReturn(activeSourceAccount);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(pendingTransaction);

        TransactionResponse response = transactionService.transfer(userId, transferRequest, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(Transaction.Status.PENDING);
        assertThat(response.getFromAccountId()).isEqualTo(fromAccountId);
        verify(sagaEventProducer).publishDebitRequest(any(DebitRequestedEvent.class));
    }

    @Test
    void transfer_shouldReturnExisting_whenIdempotencyKeyAlreadyUsed() {
        // Simulate a duplicate request: same key already present in DB
        String key = UUID.randomUUID().toString();
        when(transactionRepository.findByIdempotencyKey(key))
                .thenReturn(Optional.of(pendingTransaction));

        TransactionResponse response = transactionService.transfer(userId, transferRequest, key);

        // Must return the existing transaction without touching account or Kafka
        assertThat(response.getId()).isEqualTo(transactionId);
        verifyNoInteractions(accountClient, sagaEventProducer);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transfer_shouldThrow_whenSourceAndDestinationAreSameAccount() {
        transferRequest.setToAccountId(fromAccountId);

        assertThatThrownBy(() -> transactionService.transfer(userId, transferRequest, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("same account");

        verifyNoInteractions(accountClient, sagaEventProducer);
    }

    @Test
    void transfer_shouldThrow_whenSourceAccountNotActive() {
        activeSourceAccount.setStatus("BLOCKED");
        when(accountClient.getAccount(fromAccountId, userId)).thenReturn(activeSourceAccount);

        assertThatThrownBy(() -> transactionService.transfer(userId, transferRequest, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not active");

        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(sagaEventProducer);
    }

    @Test
    void transfer_shouldThrow_whenInsufficientFunds() {
        activeSourceAccount.setBalance(new BigDecimal("50.00"));
        when(accountClient.getAccount(fromAccountId, userId)).thenReturn(activeSourceAccount);

        assertThatThrownBy(() -> transactionService.transfer(userId, transferRequest, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient funds");

        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(sagaEventProducer);
    }

    @Test
    void transfer_shouldPublishDebitRequest_withCorrectFields() {
        when(accountClient.getAccount(fromAccountId, userId)).thenReturn(activeSourceAccount);
        when(transactionRepository.save(any())).thenReturn(pendingTransaction);

        transactionService.transfer(userId, transferRequest, null);

        verify(sagaEventProducer).publishDebitRequest(argThat(event ->
                event.getAccountId().equals(fromAccountId)
                && event.getUserId().equals(userId)
                && event.getAmount().compareTo(new BigDecimal("100.00")) == 0
        ));
    }

    // ── getHistory ─────────────────────────────────────────────────────────────

    @Test
    void getHistory_shouldReturnPageOfTransactions() {
        Page<Transaction> page = new PageImpl<>(List.of(pendingTransaction), PageRequest.of(0, 20), 1);
        when(accountClient.getAccount(fromAccountId, userId)).thenReturn(activeSourceAccount);
        when(transactionRepository.findByFromAccountIdOrToAccountIdOrderByCreatedAtDesc(
                fromAccountId, fromAccountId, PageRequest.of(0, 20))).thenReturn(page);

        Page<TransactionResponse> result = transactionService.getHistory(fromAccountId, userId, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getId()).isEqualTo(transactionId);
    }

    // ── getTransaction ─────────────────────────────────────────────────────────

    @Test
    void getTransaction_shouldReturnTransaction_whenUserIsOwner() {
        when(transactionRepository.findById(transactionId))
                .thenReturn(Optional.of(pendingTransaction));

        TransactionResponse response = transactionService.getTransaction(transactionId, userId);

        assertThat(response.getId()).isEqualTo(transactionId);
    }

    @Test
    void getTransaction_shouldThrowNotFound_whenTransactionDoesNotExist() {
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getTransaction(transactionId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Transaction not found");
    }

    @Test
    void getTransaction_shouldThrowForbidden_whenUserIsNotOwner() {
        when(transactionRepository.findById(transactionId))
                .thenReturn(Optional.of(pendingTransaction));

        assertThatThrownBy(() -> transactionService.getTransaction(transactionId, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Access denied");
    }
}
