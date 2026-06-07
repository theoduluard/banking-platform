package com.solarisbank.transaction_service.kafka.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.solarisbank.transaction_service.kafka.config.KafkaTopicConfig;
import com.solarisbank.transaction_service.kafka.event.CreditRequestedEvent;
import com.solarisbank.transaction_service.kafka.event.DebitRequestedEvent;
import com.solarisbank.transaction_service.kafka.event.TransactionCompletedEvent;
import com.solarisbank.transaction_service.kafka.event.TransactionFailedEvent;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
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

    // ── publishTransactionCompleted ───────────────────────────────────────────

    @Test
    void publishTransactionCompleted_shouldSendToCorrectTopic() {
        TransactionCompletedEvent event = TransactionCompletedEvent.builder()
                .transactionId(transactionId)
                .fromAccountId(accountId)
                .toAccountId(UUID.randomUUID())
                .senderUserId(userId)
                .amount(amount)
                .build();

        producer.publishTransactionCompleted(event);

        verify(kafkaTemplate).send(
                eq(KafkaTopicConfig.TOPIC_TRANSACTION_COMPLETED),
                eq(transactionId.toString()),
                startsWith("{"));
    }

    // ── publishTransactionFailed ──────────────────────────────────────────────

    @Test
    void publishTransactionFailed_shouldSendToCorrectTopic() {
        TransactionFailedEvent event = TransactionFailedEvent.builder()
                .transactionId(transactionId)
                .fromAccountId(accountId)
                .toAccountId(UUID.randomUUID())
                .senderUserId(userId)
                .amount(amount)
                .reason("Insufficient funds")
                .build();

        producer.publishTransactionFailed(event);

        verify(kafkaTemplate).send(
                eq(KafkaTopicConfig.TOPIC_TRANSACTION_FAILED),
                eq(transactionId.toString()),
                startsWith("{"));
    }

    // ── Serialization failure ──────────────────────────────────────────────────

    @Test
    void publishDebitRequest_shouldThrowRuntimeException_whenSerializationFails() throws Exception {
        doThrow(new JsonProcessingException("serialize error") {})
                .when(objectMapper).writeValueAsString(any());

        DebitRequestedEvent event = new DebitRequestedEvent(transactionId, accountId, userId, amount);

        assertThatThrownBy(() -> producer.publishDebitRequest(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Kafka serialization error");
    }
}
