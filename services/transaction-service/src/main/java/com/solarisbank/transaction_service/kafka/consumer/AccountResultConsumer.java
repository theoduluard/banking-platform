package com.solarisbank.transaction_service.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solarisbank.transaction_service.client.AccountClient;
import com.solarisbank.transaction_service.kafka.config.KafkaTopicConfig;
import com.solarisbank.transaction_service.kafka.event.CreditRequestedEvent;
import com.solarisbank.transaction_service.kafka.event.CreditResultEvent;
import com.solarisbank.transaction_service.kafka.event.DebitResultEvent;
import com.solarisbank.transaction_service.kafka.producer.SagaEventProducer;
import com.solarisbank.transaction_service.model.Transaction;
import com.solarisbank.transaction_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountResultConsumer {

    private final TransactionRepository transactionRepository;
    private final SagaEventProducer sagaEventProducer;
    private final AccountClient accountClient;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopicConfig.TOPIC_DEBIT_RESULT, groupId = "transaction-service")
    public void onDebitResult(String payload) {
        try {
            DebitResultEvent event = objectMapper.readValue(payload, DebitResultEvent.class);

            Transaction tx = transactionRepository.findById(event.getTransactionId()).orElse(null);
            if (tx == null) {
                log.warn("Transaction not found for debit result: {}", event.getTransactionId());
                return;
            }

            if (event.isSuccess()) {
                // Débit OK → on demande le crédit
                sagaEventProducer.publishCreditRequest(new CreditRequestedEvent(
                        tx.getId(),
                        tx.getToAccountId(),
                        tx.getFromAccountId(),
                        tx.getAmount()
                ));
                log.info("Debit OK for transaction {} → requesting credit", tx.getId());
            } else {
                // Débit KO → transaction FAILED (rien à compenser)
                tx.setStatus(Transaction.Status.FAILED);
                transactionRepository.save(tx);
                log.warn("Debit FAILED for transaction {}: {}", tx.getId(), event.getErrorMessage());
            }

        } catch (Exception e) {
            log.error("Error processing debit result", e);
        }
    }

    @KafkaListener(topics = KafkaTopicConfig.TOPIC_CREDIT_RESULT, groupId = "transaction-service")
    public void onCreditResult(String payload) {
        try {
            CreditResultEvent event = objectMapper.readValue(payload, CreditResultEvent.class);

            Transaction tx = transactionRepository.findById(event.getTransactionId()).orElse(null);
            if (tx == null) {
                log.warn("Transaction not found for credit result: {}", event.getTransactionId());
                return;
            }

            if (event.isSuccess()) {
                // Crédit OK → COMPLETED
                tx.setStatus(Transaction.Status.COMPLETED);
                tx.setCompletedAt(LocalDateTime.now());
                transactionRepository.save(tx);
                log.info("Transaction {} COMPLETED", tx.getId());
            } else {
                // Crédit KO → compensation : on re-crédite la source
                log.warn("Credit FAILED for transaction {} → compensating", tx.getId());
                try {
                    accountClient.credit(tx.getFromAccountId(), tx.getAmount());
                    log.info("Compensation OK for transaction {}", tx.getId());
                } catch (Exception e) {
                    log.error("CRITICAL: Compensation FAILED for transaction {}! Manual intervention required.", tx.getId(), e);
                }
                tx.setStatus(Transaction.Status.FAILED);
                transactionRepository.save(tx);
            }

        } catch (Exception e) {
            log.error("Error processing credit result", e);
        }
    }
}
