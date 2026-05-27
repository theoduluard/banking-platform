package com.solarisbank.account_service.service;

import com.solarisbank.account_service.dto.AccountResponse;
import com.solarisbank.account_service.dto.CreateAccountRequest;
import com.solarisbank.account_service.exception.BusinessException;
import com.solarisbank.account_service.model.Account;
import com.solarisbank.account_service.repository.AccountRepository;
import com.solarisbank.account_service.util.IbanGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private IbanGenerator ibanGenerator;

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
        when(accountRepository.findByAccountIdAndUserId(accountId, userId))
                .thenReturn(Optional.of(activeAccount));
        when(accountRepository.save(any())).thenReturn(activeAccount);

        accountService.debit(accountId, userId, new BigDecimal("100.00"));

        verify(accountRepository).save(argThat(a ->
                a.getBalance().compareTo(new BigDecimal("400.00")) == 0));
    }

    @Test
    void debit_shouldThrowNotFound_whenAccountNotFound() {
        when(accountRepository.findByAccountIdAndUserId(accountId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.debit(accountId, userId, new BigDecimal("100.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    void debit_shouldThrow_whenAccountNotActive() {
        activeAccount.setStatus(Account.Status.BLOCKED);
        when(accountRepository.findByAccountIdAndUserId(accountId, userId))
                .thenReturn(Optional.of(activeAccount));

        assertThatThrownBy(() -> accountService.debit(accountId, userId, new BigDecimal("100.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void debit_shouldThrow_whenInsufficientFunds() {
        when(accountRepository.findByAccountIdAndUserId(accountId, userId))
                .thenReturn(Optional.of(activeAccount));

        assertThatThrownBy(() -> accountService.debit(accountId, userId, new BigDecimal("999.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient funds");
    }

    // ── credit ─────────────────────────────────────────────────────────────────

    @Test
    void credit_shouldAddBalance_whenAccountActive() {
        when(accountRepository.findById(accountId))
                .thenReturn(Optional.of(activeAccount));
        when(accountRepository.save(any())).thenReturn(activeAccount);

        accountService.credit(accountId, new BigDecimal("200.00"));

        verify(accountRepository).save(argThat(a ->
                a.getBalance().compareTo(new BigDecimal("700.00")) == 0));
    }

    @Test
    void credit_shouldThrowNotFound_whenAccountNotFound() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.credit(accountId, new BigDecimal("100.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    void credit_shouldThrow_whenAccountNotActive() {
        activeAccount.setStatus(Account.Status.CLOSED);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));

        assertThatThrownBy(() -> accountService.credit(accountId, new BigDecimal("100.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not active");
    }
}
