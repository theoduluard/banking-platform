package com.solarisbank.account_service.service;

import com.solarisbank.account_service.dto.AccountResponse;
import com.solarisbank.account_service.dto.CreateAccountRequest;
import com.solarisbank.account_service.dto.VerificationDocumentRequest;
import com.solarisbank.account_service.dto.VerificationDocumentResponse;
import com.solarisbank.account_service.exception.BusinessException;
import com.solarisbank.account_service.kafka.event.AccountApprovedEvent;
import com.solarisbank.account_service.kafka.event.AccountRejectedEvent;
import com.solarisbank.account_service.kafka.producer.AccountEventProducer;
import com.solarisbank.account_service.model.Account;
import com.solarisbank.account_service.model.VerificationDocument;
import com.solarisbank.account_service.model.ProcessedSagaEvent;
import com.solarisbank.account_service.repository.AccountRepository;
import com.solarisbank.account_service.repository.ProcessedSagaEventRepository;
import com.solarisbank.account_service.repository.VerificationDocumentRepository;
import com.solarisbank.account_service.util.IbanGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final VerificationDocumentRepository documentRepository;
    private final IbanGenerator ibanGenerator;
    private final AccountEventProducer eventProducer;
    private final ProcessedSagaEventRepository processedEventRepository;

    public AccountResponse create(UUID userId, CreateAccountRequest request) {
        String iban;
        do {
            iban = ibanGenerator.generate();
        } while (accountRepository.existsByIban(iban));

        Account account = Account.builder()
                .userId(userId)
                .iban(iban)
                .type(request.getType())
                .build();

        return toResponse(accountRepository.save(account));
    }

    public List<AccountResponse> getMyAccounts(UUID userId) {
        return accountRepository.findByUserId(userId)
                .stream().map(this::toResponse).toList();
    }

    public AccountResponse getAccount(UUID accountId, UUID userId) {
        return accountRepository.findByAccountIdAndUserId(accountId, userId)
                .map(this::toResponse)
                .orElseThrow(() -> new BusinessException("Account not found", HttpStatus.NOT_FOUND));
    }

    /**
     * Returns account metadata without an ownership check.
     * Must only be called from endpoints secured by {@code X-Internal-Secret}.
     * Used by transaction-service to resolve the recipient's userId for notification events.
     */
    public AccountResponse getAccountInternal(UUID accountId) {
        return accountRepository.findById(accountId)
                .map(this::toResponse)
                .orElseThrow(() -> new BusinessException("Account not found", HttpStatus.NOT_FOUND));
    }

    public AccountResponse updateStatus(UUID accountId, UUID userId, Account.Status newStatus) {
        Account account = accountRepository.findByAccountIdAndUserId(accountId, userId)
                .orElseThrow(() -> new BusinessException("Account not found", HttpStatus.NOT_FOUND));

        account.setStatus(newStatus);
        return toResponse(accountRepository.save(account));
    }

    // ── KYC document submission ────────────────────────────────────────────────

    /**
     * Submits KYC documents for the authenticated user.
     * Called right after first login (registration flow) — no account exists yet.
     * accountId is intentionally left null; documents are looked up by userId.
     */
    @Transactional
    public void submitKyc(UUID userId, VerificationDocumentRequest request) {
        // Replace any existing KYC for this user (re-submission allowed)
        documentRepository.findByUserId(userId)
                .ifPresent(documentRepository::delete);

        VerificationDocument doc = VerificationDocument.builder()
                .accountId(null)
                .userId(userId)
                .selfieBase64(request.getSelfieBase64())
                .selfieContentType(request.getSelfieContentType())
                .idCardBase64(request.getIdCardBase64())
                .idCardContentType(request.getIdCardContentType())
                .build();

        documentRepository.save(doc);
    }

    /** Returns true if the user has already submitted their KYC documents. */
    public boolean hasKyc(UUID userId) {
        return documentRepository.existsByUserId(userId);
    }

    // ── Admin approval workflow ────────────────────────────────────────────────

    public List<AccountResponse> getPendingAccounts() {
        return accountRepository.findByStatus(Account.Status.PENDING_APPROVAL)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public AccountResponse approveAccount(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException("Account not found", HttpStatus.NOT_FOUND));

        if (account.getStatus() != Account.Status.PENDING_APPROVAL) {
            throw new BusinessException("Account is not pending approval", HttpStatus.BAD_REQUEST);
        }

        account.setStatus(Account.Status.ACTIVE);
        AccountResponse response = toResponse(accountRepository.save(account));

        // Publish Kafka event only after the DB transaction is committed.
        // Registering a synchronization here guarantees afterCommit() is called once
        // the surrounding @Transactional has successfully flushed to the database.
        // If the transaction rolls back the event is never published.
        AccountApprovedEvent approvedEvent = new AccountApprovedEvent(
                account.getAccountId(),
                account.getUserId(),
                account.getIban(),
                account.getType().name()
        );
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventProducer.publishAccountApproved(approvedEvent);
            }
        });

        return response;
    }

    @Transactional
    public AccountResponse rejectAccount(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException("Account not found", HttpStatus.NOT_FOUND));

        if (account.getStatus() != Account.Status.PENDING_APPROVAL) {
            throw new BusinessException("Account is not pending approval", HttpStatus.BAD_REQUEST);
        }

        account.setStatus(Account.Status.REJECTED);
        AccountResponse response = toResponse(accountRepository.save(account));

        // Same pattern as approveAccount — publish after commit.
        AccountRejectedEvent rejectedEvent = new AccountRejectedEvent(
                account.getAccountId(),
                account.getUserId(),
                account.getIban(),
                account.getType().name()
        );
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventProducer.publishAccountRejected(rejectedEvent);
            }
        });

        return response;
    }

    /**
     * Admin: fetch KYC documents for a given account.
     * Looks up the account to get the userId, then fetches the KYC document by userId
     * (documents are no longer keyed by accountId — they're submitted at registration).
     */
    public VerificationDocumentResponse getAccountDocuments(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException("Account not found", HttpStatus.NOT_FOUND));

        return documentRepository.findByUserId(account.getUserId())
                .map(doc -> VerificationDocumentResponse.builder()
                        .id(doc.getId())
                        .accountId(doc.getAccountId())
                        .userId(doc.getUserId())
                        .selfieBase64(doc.getSelfieBase64())
                        .selfieContentType(doc.getSelfieContentType())
                        .idCardBase64(doc.getIdCardBase64())
                        .idCardContentType(doc.getIdCardContentType())
                        .submittedAt(doc.getSubmittedAt())
                        .build())
                .orElseThrow(() -> new BusinessException("No KYC documents found for this user", HttpStatus.NOT_FOUND));
    }

    // ── Transfers ──────────────────────────────────────────────────────────────

    /**
     * Debits a user's account (ownership-checked).
     * Uses a pessimistic write lock to prevent concurrent overdraft.
     * Called directly from AccountController (synchronous REST) and from debitFromSaga.
     */
    @Transactional
    public void debit(UUID accountId, UUID userId, BigDecimal amount) {
        // SELECT … FOR UPDATE — blocks concurrent debit/credit on the same row
        Account account = accountRepository.findWithLockByAccountIdAndUserId(accountId, userId)
                .orElseThrow(() -> new BusinessException("Account not found", HttpStatus.NOT_FOUND));

        if (account.getStatus() != Account.Status.ACTIVE) {
            throw new BusinessException("Account is not active", HttpStatus.METHOD_NOT_ALLOWED);
        }
        if (account.getBalance().compareTo(amount) < 0) {
            throw new BusinessException("Insufficient funds", HttpStatus.METHOD_NOT_ALLOWED);
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
    }

    /**
     * Saga-aware debit: idempotent version called from the Kafka consumer.
     * Saves a ProcessedSagaEvent atomically with the balance update so that a
     * redelivered DebitRequested message is detected and skipped without touching
     * the balance a second time.
     * Delegates to debit() instead of duplicating its logic. Both methods share the
     * same @Transactional context (PROPAGATION.REQUIRED), so the pessimistic lock
     * acquired inside debit() is part of this transaction.
     * Any future changes to debit() (new business rules, fraud checks, etc.)
     * are automatically applied to the saga path too.
     */
    @Transactional
    public void debitFromSaga(UUID transactionId, UUID accountId, UUID userId, BigDecimal amount) {
        if (processedEventRepository.existsByTransactionIdAndEventType(transactionId, "DEBIT")) {
            log.info("[Idempotency] DEBIT for transaction {} already processed — skipping", transactionId);
            return;
        }
        // All business rules (pessimistic lock, ACTIVE check, balance check) are in debit()
        debit(accountId, userId, amount);

        // Idempotency marker — committed atomically with the balance update
        processedEventRepository.save(ProcessedSagaEvent.builder()
                .transactionId(transactionId)
                .eventType("DEBIT")
                .build());
    }

    /**
     * Credits an account (no ownership check — used for saga credit and REST compensation).
     * Uses a pessimistic write lock.
     */
    @Transactional
    public void credit(UUID accountId, BigDecimal amount) {
        Account account = accountRepository.findWithLockById(accountId)
                .orElseThrow(() -> new BusinessException("Account not found", HttpStatus.NOT_FOUND));

        if (account.getStatus() != Account.Status.ACTIVE) {
            throw new BusinessException("Account is not active", HttpStatus.METHOD_NOT_ALLOWED);
        }

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
    }

    /**
     * Saga-aware credit: idempotent version called from the Kafka consumer.
     * Saves a ProcessedSagaEvent atomically with the balance update.
     * Delegates to credit() for the same reason as debitFromSaga().
     */
    @Transactional
    public void creditFromSaga(UUID transactionId, UUID accountId, BigDecimal amount) {
        if (processedEventRepository.existsByTransactionIdAndEventType(transactionId, "CREDIT")) {
            log.info("[Idempotency] CREDIT for transaction {} already processed — skipping", transactionId);
            return;
        }
        // All business rules (pessimistic lock, ACTIVE check) are in credit()
        credit(accountId, amount);

        processedEventRepository.save(ProcessedSagaEvent.builder()
                .transactionId(transactionId)
                .eventType("CREDIT")
                .build());
    }

    // ── Account closure ───────────────────────────────────────────────────────

    /**
     * Closes the authenticated user's account.
     * Preconditions: account must be ACTIVE and have a zero balance.
     * The closure is intentionally irreversible — a closed account cannot be
     * reopened through the user-facing API.
     */
    @Transactional
    public AccountResponse closeAccount(UUID accountId, UUID userId) {
        Account account = accountRepository.findByAccountIdAndUserId(accountId, userId)
                .orElseThrow(() -> new BusinessException("Account not found", HttpStatus.NOT_FOUND));

        if (account.getStatus() != Account.Status.ACTIVE) {
            throw new BusinessException("Only active accounts can be closed", HttpStatus.BAD_REQUEST);
        }
        if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException("Account balance must be zero before closing", HttpStatus.BAD_REQUEST);
        }

        account.setStatus(Account.Status.CLOSED);
        log.info("Account {} closed by user {}", accountId, userId);
        return toResponse(accountRepository.save(account));
    }

    /** Used by the IBAN-lookup endpoint — returns the raw entity to avoid over-exposure. */
    public Optional<Account> findByIban(String iban) {
        return accountRepository.findByIban(iban);
    }

    // ── Admin balance operations (no ownership check) ─────────────────────────

    @Transactional
    public AccountResponse adminDeposit(UUID accountId, BigDecimal amount) {
        // Use pessimistic write lock — concurrent admin deposits on the same account
        // could otherwise both read the same balance and produce a lost update.
        Account account = accountRepository.findWithLockById(accountId)
                .orElseThrow(() -> new BusinessException("Account not found", HttpStatus.NOT_FOUND));

        // Require ACTIVE (not just !CLOSED) — prevents depositing into
        // PENDING_APPROVAL or REJECTED accounts, which should not hold real balances.
        if (account.getStatus() != Account.Status.ACTIVE) {
            throw new BusinessException("Account is not active", HttpStatus.METHOD_NOT_ALLOWED);
        }

        account.setBalance(account.getBalance().add(amount));
        return toResponse(accountRepository.save(account));
    }

    @Transactional
    public AccountResponse adminWithdrawal(UUID accountId, BigDecimal amount) {
        // Pessimistic write lock — same rationale as adminDeposit.
        Account account = accountRepository.findWithLockById(accountId)
                .orElseThrow(() -> new BusinessException("Account not found", HttpStatus.NOT_FOUND));

        // Require ACTIVE.
        if (account.getStatus() != Account.Status.ACTIVE) {
            throw new BusinessException("Account is not active", HttpStatus.METHOD_NOT_ALLOWED);
        }
        if (account.getBalance().compareTo(amount) < 0) {
            throw new BusinessException("Insufficient funds", HttpStatus.METHOD_NOT_ALLOWED);
        }

        account.setBalance(account.getBalance().subtract(amount));
        return toResponse(accountRepository.save(account));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private AccountResponse toResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getAccountId())
                .userId(account.getUserId())
                .iban(account.getIban())
                .type(account.getType())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .status(account.getStatus())
                .createdAt(account.getCreatedAt())
                .build();
    }
}
