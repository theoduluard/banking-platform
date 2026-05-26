package com.solarisbank.account_service.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solarisbank.account_service.kafka.config.KafkaTopicConfig;
import com.solarisbank.account_service.kafka.event.CreditRequestedEvent;
import com.solarisbank.account_service.kafka.event.CreditResultEvent;
import com.solarisbank.account_service.kafka.event.DebitRequestedEvent;
import com.solarisbank.account_service.kafka.event.DebitResultEvent;
import com.solarisbank.account_service.kafka.producer.AccountEventProducer;
import com.solarisbank.account_service.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountCommandConsumer {

    private final AccountService accountService;
    private final AccountEventProducer accountEventProducer;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopicConfig.TOPIC_DEBIT_REQUESTED, groupId = "account-service")
    public void onDebitRequested(String payload) {
        try {
            DebitRequestedEvent event = objectMapper.readValue(payload, DebitRequestedEvent.class);
            log.info("Received DebitRequested for transaction {}", event.getTransactionId());

            DebitResultEvent result;
            try {
                accountService.debit(event.getAccountId(), event.getUserId(), event.getAmount());
                result = DebitResultEvent.builder()
                        .transactionId(event.getTransactionId())
                        .success(true)
                        .build();
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
                accountService.credit(event.getToAccountId(), event.getAmount());
                result = CreditResultEvent.builder()
                        .transactionId(event.getTransactionId())
                        .success(true)
                        .build();
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
