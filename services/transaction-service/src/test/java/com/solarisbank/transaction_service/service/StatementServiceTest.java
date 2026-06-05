package com.solarisbank.transaction_service.service;

import com.solarisbank.transaction_service.client.AccountClient;
import com.solarisbank.transaction_service.client.dto.AccountResponse;
import com.solarisbank.transaction_service.exception.BusinessException;
import com.solarisbank.transaction_service.model.Transaction;
import com.solarisbank.transaction_service.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatementServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountClient         accountClient;

    @InjectMocks private StatementService statementService;

    private UUID userId;
    private UUID accountId;
    private AccountResponse accountResponse;

    @BeforeEach
    void setUp() {
        userId    = UUID.randomUUID();
        accountId = UUID.randomUUID();

        accountResponse = new AccountResponse();
        accountResponse.setId(accountId);
        accountResponse.setIban("FR7630006000010000000000197");
        accountResponse.setBalance(new BigDecimal("1250.00"));
        accountResponse.setCurrency("EUR");
        accountResponse.setStatus("ACTIVE");
        accountResponse.setType("CHECKING");
        accountResponse.setUserId(userId);
    }

    // ── generateStatement — success ────────────────────────────────────────────

    @Test
    void generateStatement_shouldReturnNonEmptyPdfBytes() {
        when(accountClient.getAccount(accountId, userId)).thenReturn(accountResponse);
        when(transactionRepository.findByFromAccountIdOrToAccountIdOrderByCreatedAtDesc(
                accountId, accountId)).thenReturn(List.of());

        byte[] pdf = statementService.generateStatement(accountId, userId);

        assertThat(pdf).isNotNull().isNotEmpty();
        // PDF magic number: %PDF
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void generateStatement_shouldIncludeTransactions_andReturnValidPdf() {
        Transaction debit = Transaction.builder()
                .id(UUID.randomUUID())
                .fromAccountId(accountId)
                .toAccountId(UUID.randomUUID())
                .initiatedByUserId(userId)
                .amount(new BigDecimal("200.00"))
                .currency("EUR")
                .type(Transaction.Type.TRANSFER)
                .status(Transaction.Status.COMPLETED)
                .description("Loyer juillet")
                .createdAt(LocalDateTime.now().minusDays(3))
                .completedAt(LocalDateTime.now().minusDays(3))
                .build();

        Transaction credit = Transaction.builder()
                .id(UUID.randomUUID())
                .fromAccountId(UUID.randomUUID())
                .toAccountId(accountId)
                .initiatedByUserId(UUID.randomUUID())
                .amount(new BigDecimal("500.00"))
                .currency("EUR")
                .type(Transaction.Type.DEPOSIT)
                .status(Transaction.Status.COMPLETED)
                .createdAt(LocalDateTime.now().minusDays(1))
                .completedAt(LocalDateTime.now().minusDays(1))
                .build();

        when(accountClient.getAccount(accountId, userId)).thenReturn(accountResponse);
        when(transactionRepository.findByFromAccountIdOrToAccountIdOrderByCreatedAtDesc(
                accountId, accountId)).thenReturn(List.of(credit, debit));

        byte[] pdf = statementService.generateStatement(accountId, userId);

        assertThat(pdf).isNotNull().hasSizeGreaterThan(100);
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void generateStatement_shouldWorkForSavingsAccount() {
        accountResponse.setType("SAVINGS");
        when(accountClient.getAccount(accountId, userId)).thenReturn(accountResponse);
        when(transactionRepository.findByFromAccountIdOrToAccountIdOrderByCreatedAtDesc(
                accountId, accountId)).thenReturn(List.of());

        byte[] pdf = statementService.generateStatement(accountId, userId);

        assertThat(pdf).isNotNull().isNotEmpty();
    }

    @Test
    void generateStatement_shouldWorkWithAllTransactionTypes() {
        List<Transaction> txs = List.of(
            buildTx(Transaction.Type.TRANSFER,   Transaction.Status.COMPLETED,  accountId, UUID.randomUUID()),
            buildTx(Transaction.Type.TRANSFER,   Transaction.Status.COMPLETED,  UUID.randomUUID(), accountId),
            buildTx(Transaction.Type.DEPOSIT,    Transaction.Status.COMPLETED,  UUID.randomUUID(), accountId),
            buildTx(Transaction.Type.WITHDRAWAL, Transaction.Status.COMPLETED,  accountId, UUID.randomUUID()),
            buildTx(Transaction.Type.TRANSFER,   Transaction.Status.PENDING,    accountId, UUID.randomUUID()),
            buildTx(Transaction.Type.TRANSFER,   Transaction.Status.FAILED,     accountId, UUID.randomUUID()),
            buildTx(Transaction.Type.TRANSFER,   Transaction.Status.CANCELLED,  accountId, UUID.randomUUID()),
            buildTx(Transaction.Type.TRANSFER,   Transaction.Status.DEBIT_CONFIRMED, accountId, UUID.randomUUID())
        );

        when(accountClient.getAccount(accountId, userId)).thenReturn(accountResponse);
        when(transactionRepository.findByFromAccountIdOrToAccountIdOrderByCreatedAtDesc(
                accountId, accountId)).thenReturn(txs);

        byte[] pdf = statementService.generateStatement(accountId, userId);
        assertThat(pdf).isNotNull().isNotEmpty();
    }

    @Test
    void generateStatement_shouldWorkWithNullDescription() {
        Transaction tx = Transaction.builder()
                .id(UUID.randomUUID())
                .fromAccountId(accountId).toAccountId(UUID.randomUUID())
                .initiatedByUserId(userId)
                .amount(new BigDecimal("50.00")).currency("EUR")
                .type(Transaction.Type.TRANSFER).status(Transaction.Status.COMPLETED)
                .description(null)   // <-- null description
                .createdAt(LocalDateTime.now()).build();

        when(accountClient.getAccount(accountId, userId)).thenReturn(accountResponse);
        when(transactionRepository.findByFromAccountIdOrToAccountIdOrderByCreatedAtDesc(
                accountId, accountId)).thenReturn(List.of(tx));

        byte[] pdf = statementService.generateStatement(accountId, userId);
        assertThat(pdf).isNotNull().isNotEmpty();
    }

    @Test
    void generateStatement_shouldWorkWithNullCurrencyOnTransaction() {
        Transaction tx = Transaction.builder()
                .id(UUID.randomUUID())
                .fromAccountId(accountId).toAccountId(UUID.randomUUID())
                .initiatedByUserId(userId)
                .amount(new BigDecimal("50.00"))
                .currency(null)   // <-- null currency → should fall back to EUR
                .type(Transaction.Type.TRANSFER).status(Transaction.Status.COMPLETED)
                .createdAt(LocalDateTime.now()).build();

        when(accountClient.getAccount(accountId, userId)).thenReturn(accountResponse);
        when(transactionRepository.findByFromAccountIdOrToAccountIdOrderByCreatedAtDesc(
                accountId, accountId)).thenReturn(List.of(tx));

        byte[] pdf = statementService.generateStatement(accountId, userId);
        assertThat(pdf).isNotNull().isNotEmpty();
    }

    // ── generateStatement — error paths ───────────────────────────────────────

    @Test
    void generateStatement_shouldPropagateException_whenAccountNotFound() {
        when(accountClient.getAccount(accountId, userId))
                .thenThrow(new BusinessException("Account not found", HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> statementService.generateStatement(accountId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Account not found");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Transaction buildTx(Transaction.Type type, Transaction.Status status,
                                 UUID from, UUID to) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .fromAccountId(from).toAccountId(to)
                .initiatedByUserId(userId)
                .amount(new BigDecimal("100.00")).currency("EUR")
                .type(type).status(status)
                .createdAt(LocalDateTime.now()).build();
    }
}
