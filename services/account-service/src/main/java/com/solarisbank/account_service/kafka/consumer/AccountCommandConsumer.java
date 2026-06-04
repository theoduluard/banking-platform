package com.solarisbank.account_service.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solarisbank.account_service.kafka.config.KafkaTopicConfig;
import com.solarisbank.account_service.kafka.event.CreditRequestedEvent;
import com.solarisbank.account_service.kafka.event.CreditResultEvent;
import com.solarisbank.account_service.kafka.event.DebitRequestedEvent;
import com.solarisbank.account_service.kafka.event.DebitResultEvent;
import com.solarisbank.account_service.kafka.producer.AccountEventProducer;
import com.solarisbank.account_service.repository.ProcessedSagaEventRepository;
import com.solarisbank.account_service.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountCommandConsumer {

    private final AccountService accountService;
    private final AccountEventProducer accountEventProducer;
    private final ObjectMapper objectMapper;
    private final ProcessedSagaEventRepository processedEventRepository;

    @KafkaListener(topics = KafkaTopicConfig.TOPIC_DEBIT_REQUESTED, groupId = "account-service")
    public void onDebitRequested(String payload) {
        try {
            DebitRequestedEvent event = objectMapper.readValue(payload, DebitRequestedEvent.class);
            log.info("Received DebitRequested for transaction {}", event.getTransactionId());

            DebitResultEvent result;
            try {
                accountService.debitFromSaga(event.getTransactionId(), event.getAccountId(), event.getUserId(), event.getAmount());
                result = DebitResultEvent.builder()
                        .transactionId(event.getTransactionId())
                        .success(true)
                        .build();
            } catch (DataIntegrityViolationException e) {
                // The unique constraint on ProcessedSagaEvent fired, meaning a concurrent
                // consumer instance processed this exact event simultaneously. The @Transactional
                // in debitFromSaga() rolled back the balance change. We re-check: if the marker
                // now exists, another instance succeeded → report success so the saga continues.
                // If the marker is absent, it's a genuine constraint error → report failure.
                boolean alreadyProcessed = processedEventRepository
                        .existsByTransactionIdAndEventType(event.getTransactionId(), "DEBIT");
                if (alreadyProcessed) {
                    log.info("[Idempotency] Concurrent duplicate DEBIT — already processed for tx {}", event.getTransactionId());
                    result = DebitResultEvent.builder()
                            .transactionId(event.getTransactionId())
                            .success(true)
                            .build();
                } else {
                    log.error("Unexpected constraint violation for DEBIT on tx {}", event.getTransactionId(), e);
                    result = DebitResultEvent.builder()
                            .transactionId(event.getTransactionId())
                            .success(false)
                            .errorMessage("Internal error during debit")
                            .build();
                }
            } catch (Exception e) {
                result = DebitResultEvent.builder()
                        .transactionId(event.getTransactionId())
                        .success(false)
                        .errorMessage(e.getMessage())
                        .build();
            }

            accountEventProducer.publishDebitResult(result);

        } catch (Exception e) {
            log.error("Error processing DebitRequested event", e);
        }
    }

    @KafkaListener(topics = KafkaTopicConfig.TOPIC_CREDIT_REQUESTED, groupId = "account-service")
    public void onCreditRequested(String payload) {
        try {
            CreditRequestedEvent event = objectMapper.readValue(payload, CreditRequestedEvent.class);
            log.info("Received CreditRequested for transaction {}", event.getTransactionId());

            CreditResultEvent result;
            try {
                accountService.creditFromSaga(event.getTransactionId(), event.getToAccountId(), event.getAmount());
                result = CreditResultEvent.builder()
                        .transactionId(event.getTransactionId())
                        .success(true)
                        .build();
            } catch (DataIntegrityViolationException e) {
                // Same rationale as the DEBIT path above.
                boolean alreadyProcessed = processedEventRepository
                        .existsByTransactionIdAndEventType(event.getTransactionId(), "CREDIT");
                if (alreadyProcessed) {
                    log.info("[Idempotency] Concurrent duplicate CREDIT — already processed for tx {}", event.getTransactionId());
                    result = CreditResultEvent.builder()
                            .transactionId(event.getTransactionId())
                            .success(true)
                            .build();
                } else {
                    log.error("Unexpected constraint violation for CREDIT on tx {}", event.getTransactionId(), e);
                    result = CreditResultEvent.builder()
                            .transactionId(event.getTransactionId())
                            .success(false)
                            .errorMessage("Internal error during credit")
                            .build();
                }
            } catch (Exception e) {
                result = CreditResultEvent.builder()
                        .transactionId(event.getTransactionId())
                        .success(false)
                        .errorMessage(e.getMessage())
                        .build();
            }

            accountEventProducer.publishCreditResult(result);

        } catch (Exception e) {
            log.error("Error processing CreditRequested event", e);
        }
    }
}
