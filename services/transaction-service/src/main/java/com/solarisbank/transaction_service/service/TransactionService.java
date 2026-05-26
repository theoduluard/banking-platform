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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountClient accountClient;
    private final SagaEventProducer sagaEventProducer;

    public TransactionResponse transfer(UUID userId, TransferRequest request) {

        // 1. Validations rapides (synchrones)
        if (request.getFromAccountId().equals(request.getToAccountId())) {
            throw new BusinessException("Cannot transfer to the same account", HttpStatus.BAD_REQUEST);
        }

        AccountResponse source = accountClient.getAccount(request.getFromAccountId(), userId);

        if (!"ACTIVE".equals(source.getStatus())) {
            throw new BusinessException("Source account is not active", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (source.getBalance().compareTo(request.getAmount()) < 0) {
            throw new BusinessException("Insufficient funds", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // 2. Créer la transaction en PENDING
        Transaction transaction = transactionRepository.save(
                Transaction.builder()
                        .fromAccountId(request.getFromAccountId())
                        .toAccountId(request.getToAccountId())
                        .initiatedByUserId(userId)
                        .amount(request.getAmount())
                        .description(request.getDescription())
                        .build()
        );

        // 3. Publier l'événement Kafka → la saga démarre (async)
        sagaEventProducer.publishDebitRequest(new DebitRequestedEvent(
                transaction.getId(),
                request.getFromAccountId(),
                userId,
                request.getAmount()
        ));

        // 4. Retourner immédiatement PENDING (202 Accepted)
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
