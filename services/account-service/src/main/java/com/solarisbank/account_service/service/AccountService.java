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
import com.solarisbank.account_service.repository.AccountRepository;
import com.solarisbank.account_service.repository.VerificationDocumentRepository;
import com.solarisbank.account_service.util.IbanGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final VerificationDocumentRepository documentRepository;
    private final IbanGenerator ibanGenerator;
    private final AccountEventProducer eventProducer;

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

        eventProducer.publishAccountApproved(new AccountApprovedEvent(
                account.getAccountId(),
                account.getUserId(),
                account.getIban(),
                account.getType().name()
        ));

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

        eventProducer.publishAccountRejected(new AccountRejectedEvent(
                account.getAccountId(),
                account.getUserId(),
                account.getIban(),
                account.getType().name()
        ));

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

    @Transactional
    public void debit(UUID accountId, UUID userId, BigDecimal amount) {
        Account account = accountRepository.findByAccountIdAndUserId(accountId, userId)
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

    @Transactional
    public void credit(UUID accountId, BigDecimal amount) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException("Account not found", HttpStatus.NOT_FOUND));

        if (account.getStatus() != Account.Status.ACTIVE) {
            throw new BusinessException("Account is not active", HttpStatus.METHOD_NOT_ALLOWED);
        }

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
    }

    /** Used by the IBAN-lookup endpoint — returns the raw entity to avoid over-exposure. */
    public Optional<Account> findByIban(String iban) {
        return accountRepository.findByIban(iban);
    }

    // ── Admin balance operations (no ownership check) ─────────────────────────

    @Transactional
    public AccountResponse adminDeposit(UUID accountId, BigDecimal amount) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException("Account not found", HttpStatus.NOT_FOUND));

        if (account.getStatus() == Account.Status.CLOSED) {
            throw new BusinessException("Cannot credit a closed account", HttpStatus.METHOD_NOT_ALLOWED);
        }

        account.setBalance(account.getBalance().add(amount));
        return toResponse(accountRepository.save(account));
    }

    @Transactional
    public AccountResponse adminWithdrawal(UUID accountId, BigDecimal amount) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException("Account not found", HttpStatus.NOT_FOUND));

        if (account.getStatus() == Account.Status.CLOSED) {
            throw new BusinessException("Cannot debit a closed account", HttpStatus.METHOD_NOT_ALLOWED);
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
