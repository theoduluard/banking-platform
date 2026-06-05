package com.solarisbank.transaction_service.kafka.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solarisbank.transaction_service.kafka.config.KafkaTopicConfig;
import com.solarisbank.transaction_service.kafka.event.CreditRequestedEvent;
import com.solarisbank.transaction_service.kafka.event.DebitRequestedEvent;
import com.solarisbank.transaction_service.kafka.event.TransactionCompletedEvent;
import com.solarisbank.transaction_service.kafka.event.TransactionFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SagaEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishDebitRequest(DebitRequestedEvent event) {
        send(KafkaTopicConfig.TOPIC_DEBIT_REQUESTED, event.getTransactionId().toString(), event);
        log.info("Published DebitRequestedEvent for transaction {}", event.getTransactionId());
    }

    public void publishCreditRequest(CreditRequestedEvent event) {
        send(KafkaTopicConfig.TOPIC_CREDIT_REQUESTED, event.getTransactionId().toString(), event);
        log.info("Published CreditRequestedEvent for transaction {}", event.getTransactionId());
    }

    public void publishTransactionCompleted(TransactionCompletedEvent event) {
        send(KafkaTopicConfig.TOPIC_TRANSACTION_COMPLETED, event.getTransactionId().toString(), event);
        log.info("Published TransactionCompletedEvent for transaction {}", event.getTransactionId());
    }

    public void publishTransactionFailed(TransactionFailedEvent event) {
        send(KafkaTopicConfig.TOPIC_TRANSACTION_FAILED, event.getTransactionId().toString(), event);
        log.info("Published TransactionFailedEvent for transaction {}", event.getTransactionId());
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
