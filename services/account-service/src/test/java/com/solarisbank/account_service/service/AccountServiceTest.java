package com.solarisbank.account_service.service;

import com.solarisbank.account_service.dto.AccountResponse;
import com.solarisbank.account_service.dto.CreateAccountRequest;
import com.solarisbank.account_service.dto.VerificationDocumentRequest;
import com.solarisbank.account_service.dto.VerificationDocumentResponse;
import com.solarisbank.account_service.exception.BusinessException;
import com.solarisbank.account_service.kafka.producer.AccountEventProducer;
import com.solarisbank.account_service.model.Account;
import com.solarisbank.account_service.model.ProcessedSagaEvent;
import com.solarisbank.account_service.model.VerificationDocument;
import com.solarisbank.account_service.repository.AccountRepository;
import com.solarisbank.account_service.repository.ProcessedSagaEventRepository;
import com.solarisbank.account_service.repository.VerificationDocumentRepository;
import com.solarisbank.account_service.util.IbanGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private AccountRepository            accountRepository;
    @Mock private VerificationDocumentRepository documentRepository;
    @Mock private IbanGenerator                ibanGenerator;
    @Mock private AccountEventProducer         eventProducer;
    @Mock private ProcessedSagaEventRepository processedEventRepository;

    @InjectMocks
    private AccountService accountService;

    private UUID userId;
    private UUID accountId;
    private Account activeAccount;
    private static final String IBAN = "FR7630006000010000000000197";

    @BeforeEach
    void setUp() {
        userId    = UUID.randomUUID();
        accountId = UUID.randomUUID();

        activeAccount = Account.builder()
                .accountId(accountId)
                .userId(userId)
                .iban(IBAN)
                .type(Account.Type.CHECKING)
                .balance(new BigDecimal("500.00"))
                .currency("EUR")
                .status(Account.Status.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ── create ─────────────────────────────────────────────────────────────────

    @Test
    void create_shouldReturnAccountResponse_whenIbanIsUnique() {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setType(Account.Type.CHECKING);

        when(ibanGenerator.generate()).thenReturn(IBAN);
        when(accountRepository.existsByIban(IBAN)).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenReturn(activeAccount);

        AccountResponse response = accountService.create(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.getIban()).isEqualTo(IBAN);
        assertThat(response.getType()).isEqualTo(Account.Type.CHECKING);
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void create_shouldRetryIbanGeneration_whenIbanAlreadyExists() {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setType(Account.Type.SAVINGS);

        String duplicateIban = "FR7600000000011111111111100";
        String uniqueIban    = "FR7600000000022222222222200";
        Account savedAccount = Account.builder()
                .accountId(accountId).userId(userId).iban(uniqueIban)
                .type(Account.Type.SAVINGS).balance(BigDecimal.ZERO)
                .currency("EUR").status(Account.Status.ACTIVE).createdAt(LocalDateTime.now()).build();

        when(ibanGenerator.generate()).thenReturn(duplicateIban, uniqueIban);
        when(accountRepository.existsByIban(duplicateIban)).thenReturn(true);
        when(accountRepository.existsByIban(uniqueIban)).thenReturn(false);
        when(accountRepository.save(any())).thenReturn(savedAccount);

        AccountResponse response = accountService.create(userId, request);

        assertThat(response.getIban()).isEqualTo(uniqueIban);
        verify(ibanGenerator, times(2)).generate();
    }

    // ── getMyAccounts ──────────────────────────────────────────────────────────

    @Test
    void getMyAccounts_shouldReturnAllUserAccounts() {
        when(accountRepository.findByUserId(userId)).thenReturn(List.of(activeAccount));

        List<AccountResponse> accounts = accountService.getMyAccounts(userId);

        assertThat(accounts).hasSize(1);
        assertThat(accounts.get(0).getIban()).isEqualTo(IBAN);
    }

    @Test
    void getMyAccounts_shouldReturnEmptyList_whenNoAccounts() {
        when(accountRepository.findByUserId(userId)).thenReturn(List.of());

        List<AccountResponse> accounts = accountService.getMyAccounts(userId);

        assertThat(accounts).isEmpty();
    }

    // ── getAccount ─────────────────────────────────────────────────────────────

    @Test
    void getAccount_shouldReturnAccount_whenFound() {
        when(accountRepository.findByAccountIdAndUserId(accountId, userId))
                .thenReturn(Optional.of(activeAccount));

        AccountResponse response = accountService.getAccount(accountId, userId);

        assertThat(response.getId()).isEqualTo(accountId);
        assertThat(response.getIban()).isEqualTo(IBAN);
    }

    @Test
    void getAccount_shouldThrowNotFound_whenAccountDoesNotBelongToUser() {
        when(accountRepository.findByAccountIdAndUserId(accountId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccount(accountId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Account not found");
    }

    // ── updateStatus ───────────────────────────────────────────────────────────

    @Test
    void updateStatus_shouldReturnUpdatedAccount_whenFound() {
        when(accountRepository.findByAccountIdAndUserId(accountId, userId))
                .thenReturn(Optional.of(activeAccount));
        when(accountRepository.save(any())).thenReturn(activeAccount);

        AccountResponse response = accountService.updateStatus(accountId, userId, Account.Status.BLOCKED);

        verify(accountRepository).save(argThat(a -> a.getStatus() == Account.Status.BLOCKED));
    }

    @Test
    void updateStatus_shouldThrowNotFound_whenAccountNotFound() {
        when(accountRepository.findByAccountIdAndUserId(accountId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.updateStatus(accountId, userId, Account.Status.BLOCKED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Account not found");
    }

    // ── debit ──────────────────────────────────────────────────────────────────

    @Test
    void debit_shouldSubtractBalance_whenSufficientFunds() {
        when(accountRepository.findWithLockByAccountIdAndUserId(accountId, userId))
                .thenReturn(Optional.of(activeAccount));
        when(accountRepository.save(any())).thenReturn(activeAccount);

        accountService.debit(accountId, userId, new BigDecimal("100.00"));

        verify(accountRepository).save(argThat(a ->
                a.getBalance().compareTo(new BigDecimal("400.00")) == 0));
    }

    @Test
    void debit_shouldThrowNotFound_whenAccountNotFound() {
        when(accountRepository.findWithLockByAccountIdAndUserId(accountId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.debit(accountId, userId, new BigDecimal("100.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    void debit_shouldThrow_whenAccountNotActive() {
        activeAccount.setStatus(Account.Status.BLOCKED);
        when(accountRepository.findWithLockByAccountIdAndUserId(accountId, userId))
                .thenReturn(Optional.of(activeAccount));

        assertThatThrownBy(() -> accountService.debit(accountId, userId, new BigDecimal("100.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void debit_shouldThrow_whenInsufficientFunds() {
        when(accountRepository.findWithLockByAccountIdAndUserId(accountId, userId))
                .thenReturn(Optional.of(activeAccount));

        assertThatThrownBy(() -> accountService.debit(accountId, userId, new BigDecimal("999.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient funds");
    }

    // ── credit ─────────────────────────────────────────────────────────────────

    @Test
    void credit_shouldAddBalance_whenAccountActive() {
        when(accountRepository.findWithLockById(accountId))
                .thenReturn(Optional.of(activeAccount));
        when(accountRepository.save(any())).thenReturn(activeAccount);

        accountService.credit(accountId, new BigDecimal("200.00"));

        verify(accountRepository).save(argThat(a ->
                a.getBalance().compareTo(new BigDecimal("700.00")) == 0));
    }

    @Test
    void credit_shouldThrowNotFound_whenAccountNotFound() {
        when(accountRepository.findWithLockById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.credit(accountId, new BigDecimal("100.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    void credit_shouldThrow_whenAccountNotActive() {
        activeAccount.setStatus(Account.Status.CLOSED);
        when(accountRepository.findWithLockById(accountId)).thenReturn(Optional.of(activeAccount));

        assertThatThrownBy(() -> accountService.credit(accountId, new BigDecimal("100.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not active");
    }

    @AfterEach
    void tearDown() {
        // Clean up TX synchronization if a test activated it manually
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ── closeAccount ───────────────────────────────────────────────────────────

    @Test
    void closeAccount_shouldSetStatusToClosed_whenActiveWithZeroBalance() {
        activeAccount.setBalance(BigDecimal.ZERO);
        Account closedAccount = Account.builder()
                .accountId(accountId).userId(userId).iban(IBAN)
                .type(Account.Type.CHECKING).balance(BigDecimal.ZERO)
                .currency("EUR").status(Account.Status.CLOSED).createdAt(LocalDateTime.now())
                .build();

        when(accountRepository.findByAccountIdAndUserId(accountId, userId))
                .thenReturn(Optional.of(activeAccount));
        when(accountRepository.save(any())).thenReturn(closedAccount);

        AccountResponse response = accountService.closeAccount(accountId, userId);

        assertThat(response.getStatus()).isEqualTo(Account.Status.CLOSED);
        verify(accountRepository).save(argThat(a -> a.getStatus() == Account.Status.CLOSED));
    }

    @Test
    void closeAccount_shouldThrowNotFound_whenAccountNotFound() {
        when(accountRepository.findByAccountIdAndUserId(accountId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.closeAccount(accountId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    void closeAccount_shouldThrowBadRequest_whenAccountNotActive() {
        activeAccount.setStatus(Account.Status.BLOCKED);
        activeAccount.setBalance(BigDecimal.ZERO);

        when(accountRepository.findByAccountIdAndUserId(accountId, userId))
                .thenReturn(Optional.of(activeAccount));

        assertThatThrownBy(() -> accountService.closeAccount(accountId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only active accounts can be closed");
    }

    @Test
    void closeAccount_shouldThrowBadRequest_whenBalanceNotZero() {
        // activeAccount already has balance 500.00
        when(accountRepository.findByAccountIdAndUserId(accountId, userId))
                .thenReturn(Optional.of(activeAccount));

        assertThatThrownBy(() -> accountService.closeAccount(accountId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("balance must be zero");
    }

    // ── approveAccount ─────────────────────────────────────────────────────────

    @Test
    void approveAccount_shouldActivateAccount_whenPending() {
        TransactionSynchronizationManager.initSynchronization();

        Account pending = Account.builder()
                .accountId(accountId).userId(userId).iban(IBAN)
                .type(Account.Type.CHECKING).balance(BigDecimal.ZERO)
                .currency("EUR").status(Account.Status.PENDING_APPROVAL)
                .createdAt(LocalDateTime.now()).build();
        Account approved = Account.builder()
                .accountId(accountId).userId(userId).iban(IBAN)
                .type(Account.Type.CHECKING).balance(BigDecimal.ZERO)
                .currency("EUR").status(Account.Status.ACTIVE)
                .createdAt(LocalDateTime.now()).build();

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(pending));
        when(accountRepository.save(any())).thenReturn(approved);

        AccountResponse response = accountService.approveAccount(accountId);

        assertThat(response.getStatus()).isEqualTo(Account.Status.ACTIVE);
        verify(accountRepository).save(argThat(a -> a.getStatus() == Account.Status.ACTIVE));
    }

    @Test
    void approveAccount_shouldThrowNotFound_whenAccountMissing() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.approveAccount(accountId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    void approveAccount_shouldThrowBadRequest_whenNotPending() {
        // activeAccount has Status.ACTIVE — not PENDING_APPROVAL
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));

        assertThatThrownBy(() -> accountService.approveAccount(accountId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not pending approval");

        verify(accountRepository, never()).save(any());
    }

    // ── rejectAccount ──────────────────────────────────────────────────────────

    @Test
    void rejectAccount_shouldRejectAccount_whenPending() {
        TransactionSynchronizationManager.initSynchronization();

        Account pending = Account.builder()
                .accountId(accountId).userId(userId).iban(IBAN)
                .type(Account.Type.CHECKING).balance(BigDecimal.ZERO)
                .currency("EUR").status(Account.Status.PENDING_APPROVAL)
                .createdAt(LocalDateTime.now()).build();
        Account rejected = Account.builder()
                .accountId(accountId).userId(userId).iban(IBAN)
                .type(Account.Type.CHECKING).balance(BigDecimal.ZERO)
                .currency("EUR").status(Account.Status.REJECTED)
                .createdAt(LocalDateTime.now()).build();

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(pending));
        when(accountRepository.save(any())).thenReturn(rejected);

        AccountResponse response = accountService.rejectAccount(accountId);

        assertThat(response.getStatus()).isEqualTo(Account.Status.REJECTED);
        verify(accountRepository).save(argThat(a -> a.getStatus() == Account.Status.REJECTED));
    }

    @Test
    void rejectAccount_shouldThrowBadRequest_whenNotPending() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));

        assertThatThrownBy(() -> accountService.rejectAccount(accountId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not pending approval");
    }

    // ── submitKyc ──────────────────────────────────────────────────────────────

    @Test
    void submitKyc_shouldSaveDocument_andDeleteExistingOne() {
        VerificationDocument existing = VerificationDocument.builder()
                .id(UUID.randomUUID()).userId(userId)
                .selfieBase64("old").selfieContentType("image/jpeg")
                .idCardBase64("old").idCardContentType("image/jpeg")
                .build();

        when(documentRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

        VerificationDocumentRequest req = new VerificationDocumentRequest();
        req.setSelfieBase64("selfieData");
        req.setSelfieContentType("image/jpeg");
        req.setIdCardBase64("idData");
        req.setIdCardContentType("image/jpeg");

        accountService.submitKyc(userId, req);

        verify(documentRepository).delete(existing);
        verify(documentRepository).save(argThat(d ->
                d.getUserId().equals(userId)
                && "selfieData".equals(d.getSelfieBase64())
                && "idData".equals(d.getIdCardBase64())));
    }

    @Test
    void submitKyc_shouldSaveDocument_whenNoExistingKyc() {
        when(documentRepository.findByUserId(userId)).thenReturn(Optional.empty());

        VerificationDocumentRequest req = new VerificationDocumentRequest();
        req.setSelfieBase64("selfieData");
        req.setSelfieContentType("image/jpeg");
        req.setIdCardBase64("idData");
        req.setIdCardContentType("image/jpeg");

        accountService.submitKyc(userId, req);

        verify(documentRepository, never()).delete(any());
        verify(documentRepository).save(any(VerificationDocument.class));
    }

    // ── hasKyc ─────────────────────────────────────────────────────────────────

    @Test
    void hasKyc_shouldReturnTrue_whenDocumentExists() {
        when(documentRepository.existsByUserId(userId)).thenReturn(true);

        assertThat(accountService.hasKyc(userId)).isTrue();
    }

    @Test
    void hasKyc_shouldReturnFalse_whenNoDocument() {
        when(documentRepository.existsByUserId(userId)).thenReturn(false);

        assertThat(accountService.hasKyc(userId)).isFalse();
    }

    // ── getPendingAccounts ─────────────────────────────────────────────────────

    @Test
    void getPendingAccounts_shouldReturnPendingList() {
        Account pending = Account.builder()
                .accountId(accountId).userId(userId).iban(IBAN)
                .type(Account.Type.CHECKING).balance(BigDecimal.ZERO)
                .currency("EUR").status(Account.Status.PENDING_APPROVAL)
                .createdAt(LocalDateTime.now()).build();

        when(accountRepository.findByStatus(Account.Status.PENDING_APPROVAL))
                .thenReturn(List.of(pending));

        List<AccountResponse> result = accountService.getPendingAccounts();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getStatus()).isEqualTo(Account.Status.PENDING_APPROVAL);
    }

    // ── getAccountDocuments ────────────────────────────────────────────────────

    @Test
    void getAccountDocuments_shouldReturnDocument_whenFound() {
        UUID docId = UUID.randomUUID();
        VerificationDocument doc = VerificationDocument.builder()
                .id(docId).accountId(null).userId(userId)
                .selfieBase64("s").selfieContentType("image/jpeg")
                .idCardBase64("d").idCardContentType("image/jpeg")
                .build();

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));
        when(documentRepository.findByUserId(userId)).thenReturn(Optional.of(doc));

        VerificationDocumentResponse response = accountService.getAccountDocuments(accountId);

        assertThat(response.getId()).isEqualTo(docId);
        assertThat(response.getSelfieBase64()).isEqualTo("s");
    }

    @Test
    void getAccountDocuments_shouldThrowNotFound_whenAccountMissing() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccountDocuments(accountId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    void getAccountDocuments_shouldThrowNotFound_whenNoKycDocuments() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));
        when(documentRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccountDocuments(accountId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No KYC documents");
    }

    // ── findByIban ─────────────────────────────────────────────────────────────

    @Test
    void findByIban_shouldReturnAccount_whenFound() {
        when(accountRepository.findByIban(IBAN)).thenReturn(Optional.of(activeAccount));

        assertThat(accountService.findByIban(IBAN)).contains(activeAccount);
    }

    @Test
    void findByIban_shouldReturnEmpty_whenNotFound() {
        when(accountRepository.findByIban(IBAN)).thenReturn(Optional.empty());

        assertThat(accountService.findByIban(IBAN)).isEmpty();
    }

    // ── adminDeposit ───────────────────────────────────────────────────────────

    @Test
    void adminDeposit_shouldAddBalance_whenAccountActive() {
        when(accountRepository.findWithLockById(accountId)).thenReturn(Optional.of(activeAccount));
        when(accountRepository.save(any())).thenReturn(activeAccount);

        AccountResponse response = accountService.adminDeposit(accountId, new BigDecimal("200.00"));

        verify(accountRepository).save(argThat(a ->
                a.getBalance().compareTo(new BigDecimal("700.00")) == 0));
    }

    @Test
    void adminDeposit_shouldThrowNotFound_whenAccountMissing() {
        when(accountRepository.findWithLockById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.adminDeposit(accountId, new BigDecimal("100.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    void adminDeposit_shouldThrow_whenAccountNotActive() {
        activeAccount.setStatus(Account.Status.BLOCKED);
        when(accountRepository.findWithLockById(accountId)).thenReturn(Optional.of(activeAccount));

        assertThatThrownBy(() -> accountService.adminDeposit(accountId, new BigDecimal("100.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not active");
    }

    // ── adminWithdrawal ────────────────────────────────────────────────────────

    @Test
    void adminWithdrawal_shouldSubtractBalance_whenSufficientFunds() {
        when(accountRepository.findWithLockById(accountId)).thenReturn(Optional.of(activeAccount));
        when(accountRepository.save(any())).thenReturn(activeAccount);

        accountService.adminWithdrawal(accountId, new BigDecimal("100.00"));

        verify(accountRepository).save(argThat(a ->
                a.getBalance().compareTo(new BigDecimal("400.00")) == 0));
    }

    @Test
    void adminWithdrawal_shouldThrow_whenInsufficientFunds() {
        when(accountRepository.findWithLockById(accountId)).thenReturn(Optional.of(activeAccount));

        assertThatThrownBy(() -> accountService.adminWithdrawal(accountId, new BigDecimal("9999.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient funds");
    }

    @Test
    void adminWithdrawal_shouldThrow_whenAccountNotActive() {
        activeAccount.setStatus(Account.Status.CLOSED);
        when(accountRepository.findWithLockById(accountId)).thenReturn(Optional.of(activeAccount));

        assertThatThrownBy(() -> accountService.adminWithdrawal(accountId, new BigDecimal("100.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not active");
    }

    // ── debitFromSaga ──────────────────────────────────────────────────────────

    @Test
    void debitFromSaga_shouldSkipProcessing_whenAlreadyProcessed() {
        UUID txId = UUID.randomUUID();
        when(processedEventRepository.existsByTransactionIdAndEventType(txId, "DEBIT"))
                .thenReturn(true);

        accountService.debitFromSaga(txId, accountId, userId, new BigDecimal("100.00"));

        verifyNoInteractions(accountRepository);
    }

    @Test
    void debitFromSaga_shouldDebitAndSaveMarker_whenNotYetProcessed() {
        UUID txId = UUID.randomUUID();
        when(processedEventRepository.existsByTransactionIdAndEventType(txId, "DEBIT"))
                .thenReturn(false);
        when(accountRepository.findWithLockByAccountIdAndUserId(accountId, userId))
                .thenReturn(Optional.of(activeAccount));
        when(accountRepository.save(any())).thenReturn(activeAccount);
        when(processedEventRepository.save(any(ProcessedSagaEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        accountService.debitFromSaga(txId, accountId, userId, new BigDecimal("100.00"));

        verify(accountRepository).save(argThat(a ->
                a.getBalance().compareTo(new BigDecimal("400.00")) == 0));
        verify(processedEventRepository).save(argThat(e ->
                txId.equals(e.getTransactionId()) && "DEBIT".equals(e.getEventType())));
    }

    // ── creditFromSaga ─────────────────────────────────────────────────────────

    @Test
    void creditFromSaga_shouldSkipProcessing_whenAlreadyProcessed() {
        UUID txId = UUID.randomUUID();
        when(processedEventRepository.existsByTransactionIdAndEventType(txId, "CREDIT"))
                .thenReturn(true);

        accountService.creditFromSaga(txId, accountId, new BigDecimal("100.00"));

        verifyNoInteractions(accountRepository);
    }

    @Test
    void creditFromSaga_shouldCreditAndSaveMarker_whenNotYetProcessed() {
        UUID txId = UUID.randomUUID();
        when(processedEventRepository.existsByTransactionIdAndEventType(txId, "CREDIT"))
                .thenReturn(false);
        when(accountRepository.findWithLockById(accountId)).thenReturn(Optional.of(activeAccount));
        when(accountRepository.save(any())).thenReturn(activeAccount);
        when(processedEventRepository.save(any(ProcessedSagaEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        accountService.creditFromSaga(txId, accountId, new BigDecimal("200.00"));

        verify(accountRepository).save(argThat(a ->
                a.getBalance().compareTo(new BigDecimal("700.00")) == 0));
        verify(processedEventRepository).save(argThat(e ->
                txId.equals(e.getTransactionId()) && "CREDIT".equals(e.getEventType())));
    }
}
