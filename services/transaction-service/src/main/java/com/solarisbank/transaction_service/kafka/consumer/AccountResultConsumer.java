package com.solarisbank.transaction_service.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solarisbank.transaction_service.client.AccountClient;
import com.solarisbank.transaction_service.kafka.config.KafkaTopicConfig;
import com.solarisbank.transaction_service.kafka.event.CreditRequestedEvent;
import com.solarisbank.transaction_service.kafka.event.CreditResultEvent;
import com.solarisbank.transaction_service.kafka.event.DebitResultEvent;
import com.solarisbank.transaction_service.kafka.event.TransactionCompletedEvent;
import com.solarisbank.transaction_service.kafka.event.TransactionFailedEvent;
import com.solarisbank.transaction_service.kafka.producer.SagaEventProducer;
import com.solarisbank.transaction_service.model.Transaction;
import com.solarisbank.transaction_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountResultConsumer {

    private final TransactionRepository transactionRepository;
    private final SagaEventProducer sagaEventProducer;
    private final AccountClient accountClient;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopicConfig.TOPIC_DEBIT_RESULT, groupId = "transaction-service")
    @Transactional
    // @Transactional ensures that if publishCreditRequest() throws (Kafka broker down),
    // the DEBIT_CONFIRMED save is rolled back atomically. The exception propagates out of the
    // method, Kafka does not commit the offset, and the message is redelivered. On retry,
    // status is still PENDING so the guard passes and the whole step is retried correctly.
    public void onDebitResult(String payload) {
        DebitResultEvent event;
        try {
            event = objectMapper.readValue(payload, DebitResultEvent.class);
        } catch (Exception e) {
            // Poison-pill: unparseable message — log and skip permanently (no retry)
            log.error("Unparseable DebitResult payload, skipping: {}", payload, e);
            return;
        }

        Transaction tx = transactionRepository.findById(event.getTransactionId()).orElse(null);
        if (tx == null) {
            log.warn("Transaction not found for debit result: {}", event.getTransactionId());
            return;
        }

        // ── Idempotency guard ──────────────────────────────────────────────
        // Only process if the transaction is still PENDING.
        // A redelivered DebitResult would find status=DEBIT_CONFIRMED (or FAILED/COMPLETED)
        // and be safely ignored, preventing a second CreditRequested from being published.
        if (tx.getStatus() != Transaction.Status.PENDING) {
            log.warn("[Idempotency] Skipping duplicate DebitResult for transaction {} (status={})",
                    tx.getId(), tx.getStatus());
            return;
        }

        if (event.isSuccess()) {
            // Transition to DEBIT_CONFIRMED before publishing CreditRequested.
            // If publishCreditRequest() throws, @Transactional rolls back this save → status
            // stays PENDING → Kafka redelivers → guard passes → retried correctly.
            tx.setStatus(Transaction.Status.DEBIT_CONFIRMED);
            transactionRepository.save(tx);
            sagaEventProducer.publishCreditRequest(new CreditRequestedEvent(
                    tx.getId(),
                    tx.getToAccountId(),
                    tx.getFromAccountId(),
                    tx.getAmount()
            ));
            log.info("Debit OK for transaction {} → requesting credit", tx.getId());
        } else {
            // Débit KO → transaction FAILED (nothing to compensate)
            tx.setStatus(Transaction.Status.FAILED);
            transactionRepository.save(tx);
            log.warn("Debit FAILED for transaction {}: {}", tx.getId(), event.getErrorMessage());
            // Notify sender that the transfer failed
            sagaEventProducer.publishTransactionFailed(TransactionFailedEvent.builder()
                    .transactionId(tx.getId())
                    .fromAccountId(tx.getFromAccountId())
                    .toAccountId(tx.getToAccountId())
                    .senderUserId(tx.getInitiatedByUserId())
                    .amount(tx.getAmount())
                    .currency(tx.getCurrency())
                    .description(tx.getDescription())
                    .reason(event.getErrorMessage())
                    .failedAt(LocalDateTime.now())
                    .build());
        }
    }

    @KafkaListener(topics = KafkaTopicConfig.TOPIC_CREDIT_RESULT, groupId = "transaction-service")
    @Transactional
    // Same @Transactional rationale as onDebitResult.
    public void onCreditResult(String payload) {
        CreditResultEvent event;
        try {
            event = objectMapper.readValue(payload, CreditResultEvent.class);
        } catch (Exception e) {
            log.error("Unparseable CreditResult payload, skipping: {}", payload, e);
            return;
        }

        Transaction tx = transactionRepository.findById(event.getTransactionId()).orElse(null);
        if (tx == null) {
            log.warn("Transaction not found for credit result: {}", event.getTransactionId());
            return;
        }

        // ── Idempotency guard ──────────────────────────────────────────────
        // Only process if debit was confirmed (the expected preceding state).
        // A redelivered CreditResult would find status=COMPLETED or FAILED and be ignored.
        if (tx.getStatus() != Transaction.Status.DEBIT_CONFIRMED) {
            log.warn("[Idempotency] Skipping duplicate CreditResult for transaction {} (status={})",
                    tx.getId(), tx.getStatus());
            return;
        }

        if (event.isSuccess()) {
            // Crédit OK → COMPLETED
            tx.setStatus(Transaction.Status.COMPLETED);
            LocalDateTime now = LocalDateTime.now();
            tx.setCompletedAt(now);
            transactionRepository.save(tx);
            log.info("Transaction {} COMPLETED", tx.getId());

            // Resolve recipient's userId to fan-out notifications to both parties.
            // Best-effort: if the lookup fails we still notify the sender.
            UUID recipientUserId = null;
            try {
                var recipientAccount = accountClient.getAccountInternal(tx.getToAccountId());
                recipientUserId = recipientAccount.getUserId();
            } catch (Exception e) {
                log.warn("Could not resolve recipient userId for transaction {} — recipient will not be notified: {}",
                        tx.getId(), e.getMessage());
            }
            sagaEventProducer.publishTransactionCompleted(TransactionCompletedEvent.builder()
                    .transactionId(tx.getId())
                    .fromAccountId(tx.getFromAccountId())
                    .toAccountId(tx.getToAccountId())
                    .senderUserId(tx.getInitiatedByUserId())
                    .recipientUserId(recipientUserId)
                    .amount(tx.getAmount())
                    .currency(tx.getCurrency())
                    .description(tx.getDescription())
                    .completedAt(now)
                    .build());
        } else {
            // Set status to FAILED and persist BEFORE calling the compensation REST endpoint.
            // If we crash between the credit() call and the save(FAILED), a redelivered CreditResult
            // would find status=DEBIT_CONFIRMED, re-enter this branch and call credit() again —
            // double-crediting the source account. By saving FAILED first, any redelivery hits the
            // guard above (status != DEBIT_CONFIRMED) and is safely ignored.
            // Trade-off: compensation becomes best-effort. If the REST call fails after we committed
            // FAILED, it won't be retried automatically. The log.error below triggers ops alerting.
            log.warn("Credit FAILED for transaction {} → compensating", tx.getId());
            tx.setStatus(Transaction.Status.FAILED);
            transactionRepository.save(tx);
            try {
                accountClient.credit(tx.getFromAccountId(), tx.getAmount());
                log.info("Compensation OK for transaction {}", tx.getId());
            } catch (Exception e) {
                log.error("CRITICAL: Compensation FAILED for transaction {}! Manual intervention required.", tx.getId(), e);
            }
            sagaEventProducer.publishTransactionFailed(TransactionFailedEvent.builder()
                    .transactionId(tx.getId())
                    .fromAccountId(tx.getFromAccountId())
                    .toAccountId(tx.getToAccountId())
                    .senderUserId(tx.getInitiatedByUserId())
                    .amount(tx.getAmount())
                    .currency(tx.getCurrency())
                    .description(tx.getDescription())
                    .reason(event.getErrorMessage())
                    .failedAt(LocalDateTime.now())
                    .build());
        }
    }
}
