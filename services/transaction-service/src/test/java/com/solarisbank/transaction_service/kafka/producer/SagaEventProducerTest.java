package com.solarisbank.transaction_service.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.solarisbank.transaction_service.kafka.config.KafkaTopicConfig;
import com.solarisbank.transaction_service.kafka.event.CreditRequestedEvent;
import com.solarisbank.transaction_service.kafka.event.DebitRequestedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SagaEventProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @InjectMocks
    private SagaEventProducer producer;

    private UUID transactionId;
    private UUID accountId;
    private UUID userId;
    private BigDecimal amount;

    @BeforeEach
    void setUp() {
        transactionId = UUID.randomUUID();
        accountId     = UUID.randomUUID();
        userId        = UUID.randomUUID();
        amount        = new BigDecimal("150.00");
    }

    // ── publishDebitRequest ────────────────────────────────────────────────────

    @Test
    void publishDebitRequest_shouldSendToCorrectTopic() {
        DebitRequestedEvent event = new DebitRequestedEvent(transactionId, accountId, userId, amount);

        producer.publishDebitRequest(event);

        verify(kafkaTemplate).send(
                eq(KafkaTopicConfig.TOPIC_DEBIT_REQUESTED),
                eq(transactionId.toString()),
                startsWith("{"));
    }

    @Test
    void publishDebitRequest_shouldUseTransactionId_asKey() {
        DebitRequestedEvent event = new DebitRequestedEvent(transactionId, accountId, userId, amount);

        producer.publishDebitRequest(event);

        verify(kafkaTemplate).send(
                eq(KafkaTopicConfig.TOPIC_DEBIT_REQUESTED),
                eq(transactionId.toString()),
                startsWith("{"));
    }

    // ── publishCreditRequest ───────────────────────────────────────────────────

    @Test
    void publishCreditRequest_shouldSendToCorrectTopic() {
        UUID toAccountId   = UUID.randomUUID();
        UUID fromAccountId = UUID.randomUUID();
        CreditRequestedEvent event = new CreditRequestedEvent(transactionId, toAccountId, fromAccountId, amount);

        producer.publishCreditRequest(event);

        verify(kafkaTemplate).send(
                eq(KafkaTopicConfig.TOPIC_CREDIT_REQUESTED),
                eq(transactionId.toString()),
                startsWith("{"));
    }

    @Test
    void publishCreditRequest_shouldUseTransactionId_asKey() {
        UUID toAccountId   = UUID.randomUUID();
        UUID fromAccountId = UUID.randomUUID();
        CreditRequestedEvent event = new CreditRequestedEvent(transactionId, toAccountId, fromAccountId, amount);

        producer.publishCreditRequest(event);

        verify(kafkaTemplate).send(
                eq(KafkaTopicConfig.TOPIC_CREDIT_REQUESTED),
                eq(transactionId.toString()),
                startsWith("{"));
    }
}
