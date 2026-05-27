package com.solarisbank.account_service.repository;

import com.solarisbank.account_service.model.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AccountRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AccountRepository accountRepository;

    private UUID userId;
    private Account account;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        account = Account.builder()
                .userId(userId)
                .iban("FR7630006000010000000000197")
                .type(Account.Type.CHECKING)
                .build();
        // @PrePersist remplit balance, status, currency, createdAt
        entityManager.persistAndFlush(account);
    }

    // ── findByUserId ───────────────────────────────────────────────────────────

    @Test
    void findByUserId_shouldReturnAccounts_forGivenUser() {
        List<Account> accounts = accountRepository.findByUserId(userId);

        assertThat(accounts).hasSize(1);
        assertThat(accounts.get(0).getIban()).isEqualTo("FR7630006000010000000000197");
    }

    @Test
    void findByUserId_shouldReturnEmpty_forUnknownUser() {
        List<Account> accounts = accountRepository.findByUserId(UUID.randomUUID());

        assertThat(accounts).isEmpty();
    }

    @Test
    void findByUserId_shouldReturnMultipleAccounts_whenUserHasMany() {
        Account second = Account.builder()
                .userId(userId)
                .iban("FR7630006000010000000000214")
                .type(Account.Type.SAVINGS)
                .build();
        entityManager.persistAndFlush(second);

        List<Account> accounts = accountRepository.findByUserId(userId);

        assertThat(accounts).hasSize(2);
    }

    // ── existsByIban ───────────────────────────────────────────────────────────

    @Test
    void existsByIban_shouldReturnTrue_whenIbanExists() {
        assertThat(accountRepository.existsByIban("FR7630006000010000000000197")).isTrue();
    }

    @Test
    void existsByIban_shouldReturnFalse_whenIbanDoesNotExist() {
        assertThat(accountRepository.existsByIban("FR0000000000000000000000000")).isFalse();
    }

    // ── findByAccountIdAndUserId ───────────────────────────────────────────────

    @Test
    void findByAccountIdAndUserId_shouldReturnAccount_whenBothMatch() {
        Optional<Account> result = accountRepository
                .findByAccountIdAndUserId(account.getAccountId(), userId);

        assertThat(result).isPresent();
        assertThat(result.get().getIban()).isEqualTo("FR7630006000010000000000197");
    }

    @Test
    void findByAccountIdAndUserId_shouldReturnEmpty_whenUserDoesNotMatch() {
        Optional<Account> result = accountRepository
                .findByAccountIdAndUserId(account.getAccountId(), UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    @Test
    void findByAccountIdAndUserId_shouldReturnEmpty_whenAccountDoesNotExist() {
        Optional<Account> result = accountRepository
                .findByAccountIdAndUserId(UUID.randomUUID(), userId);

        assertThat(result).isEmpty();
    }
}
