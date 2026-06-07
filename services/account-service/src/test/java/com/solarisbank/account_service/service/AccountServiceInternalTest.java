package com.solarisbank.account_service.service;

import com.solarisbank.account_service.dto.AccountResponse;
import com.solarisbank.account_service.exception.BusinessException;
import com.solarisbank.account_service.kafka.producer.AccountEventProducer;
import com.solarisbank.account_service.model.Account;
import com.solarisbank.account_service.repository.AccountRepository;
import com.solarisbank.account_service.repository.ProcessedSagaEventRepository;
import com.solarisbank.account_service.repository.VerificationDocumentRepository;
import com.solarisbank.account_service.util.IbanGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Covers AccountService.getAccountInternal — the bypass endpoint used by
 * transaction-service to resolve the recipient's userId without an ownership check.
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceInternalTest {

    @Mock private AccountRepository              accountRepository;
    @Mock private VerificationDocumentRepository documentRepository;
    @Mock private IbanGenerator                  ibanGenerator;
    @Mock private AccountEventProducer           eventProducer;
    @Mock private ProcessedSagaEventRepository   processedEventRepository;

    @InjectMocks
    private AccountService accountService;

    private UUID accountId;
    private UUID userId;
    private Account activeAccount;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        userId    = UUID.randomUUID();

        activeAccount = Account.builder()
                .accountId(accountId)
                .userId(userId)
                .iban("FR7630006000010000000000197")
                .type(Account.Type.CHECKING)
                .status(Account.Status.ACTIVE)
                .balance(new BigDecimal("1500.00"))
                .currency("EUR")
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ── getAccountInternal ─────────────────────────────────────────────────────

    @Test
    void getAccountInternal_shouldReturnAccount_withoutOwnershipCheck() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));

        AccountResponse response = accountService.getAccountInternal(accountId);

        assertThat(response.getId()).isEqualTo(accountId);
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getIban()).isEqualTo("FR7630006000010000000000197");
        assertThat(response.getBalance()).isEqualByComparingTo("1500.00");
        assertThat(response.getStatus()).isEqualTo(Account.Status.ACTIVE);
    }

    @Test
    void getAccountInternal_shouldThrowNotFound_whenAccountDoesNotExist() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccountInternal(accountId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Account not found")
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getAccountInternal_shouldWorkForAnyAccountRegardlessOfOwner() {
        // Even if userId belongs to another user, findById should succeed
        UUID differentUser = UUID.randomUUID();
        activeAccount.setUserId(differentUser);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));

        AccountResponse response = accountService.getAccountInternal(accountId);

        assertThat(response.getUserId()).isEqualTo(differentUser);
    }

    @Test
    void getAccountInternal_shouldReturnCorrectType_forSavingsAccount() {
        activeAccount.setType(Account.Type.SAVINGS);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));

        AccountResponse response = accountService.getAccountInternal(accountId);

        assertThat(response.getType()).isEqualTo(Account.Type.SAVINGS);
    }
}
