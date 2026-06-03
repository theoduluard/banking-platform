package com.solarisbank.transaction_service.service;

import com.solarisbank.transaction_service.client.AccountClient;
import com.solarisbank.transaction_service.client.dto.AccountResponse;
import com.solarisbank.transaction_service.dto.AdminOperationRequest;
import com.solarisbank.transaction_service.dto.TransactionResponse;
import com.solarisbank.transaction_service.dto.TransferRequest;
import com.solarisbank.transaction_service.exception.BusinessException;
import com.solarisbank.transaction_service.kafka.event.DebitRequestedEvent;
import com.solarisbank.transaction_service.kafka.producer.SagaEventProducer;
import com.solarisbank.transaction_service.model.Transaction;
import com.solarisbank.transaction_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountClient accountClient;
    private final SagaEventProducer sagaEventProducer;

    @Transactional
    public TransactionResponse transfer(UUID userId, TransferRequest request, String idempotencyKey) {

        // ── Idempotency check ──────────────────────────────────────────────────
        // If the client sent a key and we already have a transaction for it,
        // return the existing response without processing anything again.
        // This handles: double-clicks, network retries, duplicate form submissions.
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("[Idempotency] Duplicate request blocked — key={}", idempotencyKey);
                return toResponse(existing.get());
            }
        }

        // ── 1. Validations rapides (synchrones) ───────────────────────────────
        if (request.getFromAccountId().equals(request.getToAccountId())) {
            throw new BusinessException("Cannot transfer to the same account", HttpStatus.BAD_REQUEST);
        }

        AccountResponse source = accountClient.getAccount(request.getFromAccountId(), userId);

        if (!"ACTIVE".equals(source.getStatus())) {
            throw new BusinessException("Source account is not active", HttpStatus.METHOD_NOT_ALLOWED);
        }
        if (source.getBalance().compareTo(request.getAmount()) < 0) {
            throw new BusinessException("Insufficient funds", HttpStatus.METHOD_NOT_ALLOWED);
        }

        // ── 2. Créer la transaction en PENDING ────────────────────────────────
        // The idempotency key is stored here. The UNIQUE constraint in the DB
        // is the last line of defence against concurrent duplicate requests.
        Transaction transaction = transactionRepository.save(
                Transaction.builder()
                        .fromAccountId(request.getFromAccountId())
                        .toAccountId(request.getToAccountId())
                        .initiatedByUserId(userId)
                        .amount(request.getAmount())
                        .description(request.getDescription())
                        .idempotencyKey(idempotencyKey)
                        .build()
        );

        // ── 3. Publier l'événement Kafka → la saga démarre (async) ───────────
        sagaEventProducer.publishDebitRequest(new DebitRequestedEvent(
                transaction.getId(),
                request.getFromAccountId(),
                userId,
                request.getAmount()
        ));

        // ── 4. Retourner immédiatement PENDING (202 Accepted) ─────────────────
        return toResponse(transaction);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getHistory(UUID accountId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return transactionRepository
                .findByFromAccountIdOrToAccountIdOrderByCreatedAtDesc(accountId, accountId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(UUID transactionId, UUID userId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new BusinessException("Transaction not found", HttpStatus.NOT_FOUND));

        if (!transaction.getInitiatedByUserId().equals(userId)) {
            throw new BusinessException("Access denied", HttpStatus.FORBIDDEN);
        }

        return toResponse(transaction);
    }

    // ── Admin operations ──────────────────────────────────────────────────────

    /** Sentinel UUID representing the bank/system account (no real account behind it). */
    private static final UUID SYSTEM_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    /**
     * Admin deposit: credit the target account and record a DEPOSIT transaction.
     * Synchronous — no Kafka saga. The balance is updated immediately.
     *
     * Order: save the audit record first (inside @Transactional), then call account-service.
     * If the account-service call throws, @Transactional rolls back the audit record,
     * leaving the DB clean. The residual risk (DB commit fails after REST succeeds) is
     * extremely unlikely in practice and far less common than the inverse.
     */
    @Transactional
    public TransactionResponse adminDeposit(UUID adminId, AdminOperationRequest request) {
        Transaction tx = transactionRepository.save(Transaction.builder()
                .fromAccountId(SYSTEM_ACCOUNT_ID)
                .toAccountId(request.getAccountId())
                .initiatedByUserId(adminId)
                .amount(request.getAmount())
                .description(request.getDescription())
                .type(Transaction.Type.DEPOSIT)
                .status(Transaction.Status.COMPLETED)
                .completedAt(LocalDateTime.now())
                .build());

        // If this throws, @Transactional rolls back the audit record above
        accountClient.adminDeposit(request.getAccountId(), request.getAmount());

        log.info("[Admin] Deposit {} EUR on account {} by admin {}",
                request.getAmount(), request.getAccountId(), adminId);
        return toResponse(tx);
    }

    /**
     * Admin withdrawal: debit the target account and record a WITHDRAWAL transaction.
     * Synchronous — no Kafka saga. The balance is updated immediately.
     * Same ordering strategy as adminDeposit.
     */
    @Transactional
    public TransactionResponse adminWithdrawal(UUID adminId, AdminOperationRequest request) {
        Transaction tx = transactionRepository.save(Transaction.builder()
                .fromAccountId(request.getAccountId())
                .toAccountId(SYSTEM_ACCOUNT_ID)
                .initiatedByUserId(adminId)
                .amount(request.getAmount())
                .description(request.getDescription())
                .type(Transaction.Type.WITHDRAWAL)
                .status(Transaction.Status.COMPLETED)
                .completedAt(LocalDateTime.now())
                .build());

        // If this throws, @Transactional rolls back the audit record above
        accountClient.adminWithdrawal(request.getAccountId(), request.getAmount());

        log.info("[Admin] Withdrawal {} EUR from account {} by admin {}",
                request.getAmount(), request.getAccountId(), adminId);
        return toResponse(tx);
    }

    private TransactionResponse toResponse(Transaction t) {
        return TransactionResponse.builder()
                .id(t.getId())
                .fromAccountId(t.getFromAccountId())
                .toAccountId(t.getToAccountId())
                .amount(t.getAmount())
                .currency(t.getCurrency())
                .type(t.getType())
                .status(t.getStatus())
                .description(t.getDescription())
                .createdAt(t.getCreatedAt())
                .completedAt(t.getCompletedAt())
                .build();
    }
}
