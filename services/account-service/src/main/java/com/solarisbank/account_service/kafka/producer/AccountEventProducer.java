package com.solarisbank.account_service.kafka.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solarisbank.account_service.kafka.config.KafkaTopicConfig;
import com.solarisbank.account_service.kafka.event.AccountApprovedEvent;
import com.solarisbank.account_service.kafka.event.AccountRejectedEvent;
import com.solarisbank.account_service.kafka.event.CreditResultEvent;
import com.solarisbank.account_service.kafka.event.DebitResultEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishDebitResult(DebitResultEvent event) {
        send(KafkaTopicConfig.TOPIC_DEBIT_RESULT, event.getTransactionId().toString(), event);
        log.info("Published DebitResult [success={}] for transaction {}", event.isSuccess(), event.getTransactionId());
    }

    public void publishCreditResult(CreditResultEvent event) {
        send(KafkaTopicConfig.TOPIC_CREDIT_RESULT, event.getTransactionId().toString(), event);
        log.info("Published CreditResult [success={}] for transaction {}", event.isSuccess(), event.getTransactionId());
    }

    public void publishAccountApproved(AccountApprovedEvent event) {
        send(KafkaTopicConfig.TOPIC_ACCOUNT_APPROVED, event.getAccountId().toString(), event);
        log.info("Published AccountApproved for account {}", event.getAccountId());
    }

    public void publishAccountRejected(AccountRejectedEvent event) {
        send(KafkaTopicConfig.TOPIC_ACCOUNT_REJECTED, event.getAccountId().toString(), event);
        log.info("Published AccountRejected for account {}", event.getAccountId());
    }

    private void send(String topic, String key, Object payload) {
        try {
            kafkaTemplate.send(topic, key, objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event for topic {}", topic, e);
            throw new RuntimeException("Kafka serialization error", e);
        }
    }
}
