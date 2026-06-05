package com.solarisbank.account_service.kafka.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.solarisbank.account_service.kafka.config.KafkaTopicConfig;
import com.solarisbank.account_service.kafka.event.AccountApprovedEvent;
import com.solarisbank.account_service.kafka.event.AccountRejectedEvent;
import com.solarisbank.account_service.kafka.event.CreditResultEvent;
import com.solarisbank.account_service.kafka.event.DebitResultEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountEventProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @InjectMocks
    private AccountEventProducer producer;

    private UUID transactionId;

    @BeforeEach
    void setUp() {
        transactionId = UUID.randomUUID();
    }

    // ── publishDebitResult ─────────────────────────────────────────────────────

    @Test
    void publishDebitResult_shouldSendToCorrectTopic() {
        DebitResultEvent event = DebitResultEvent.builder()
                .transactionId(transactionId)
                .success(true)
                .build();

        producer.publishDebitResult(event);

        verify(kafkaTemplate).send(
                eq(KafkaTopicConfig.TOPIC_DEBIT_RESULT),
                eq(transactionId.toString()),
                startsWith("{"));
    }

    @Test
    void publishDebitResult_shouldIncludeSuccessFlag_inPayload() throws Exception {
        DebitResultEvent event = DebitResultEvent.builder()
                .transactionId(transactionId)
                .success(false)
                .errorMessage("Insufficient funds")
                .build();

        producer.publishDebitResult(event);

        verify(kafkaTemplate).send(
                eq(KafkaTopicConfig.TOPIC_DEBIT_RESULT),
                eq(transactionId.toString()),
                startsWith("{"));
    }

    // ── publishCreditResult ────────────────────────────────────────────────────

    @Test
    void publishCreditResult_shouldSendToCorrectTopic() {
        CreditResultEvent event = CreditResultEvent.builder()
                .transactionId(transactionId)
                .success(true)
                .build();

        producer.publishCreditResult(event);

        verify(kafkaTemplate).send(
                eq(KafkaTopicConfig.TOPIC_CREDIT_RESULT),
                eq(transactionId.toString()),
                startsWith("{"));
    }

    @Test
    void publishCreditResult_shouldIncludeFailureInfo_inPayload() {
        CreditResultEvent event = CreditResultEvent.builder()
                .transactionId(transactionId)
                .success(false)
                .errorMessage("Account closed")
                .build();

        producer.publishCreditResult(event);

        verify(kafkaTemplate).send(
                eq(KafkaTopicConfig.TOPIC_CREDIT_RESULT),
                eq(transactionId.toString()),
                startsWith("{"));
    }

    // ── publishAccountApproved ─────────────────────────────────────────────────

    @Test
    void publishAccountApproved_shouldSendToCorrectTopic() {
        UUID accountId = UUID.randomUUID();
        UUID userId    = UUID.randomUUID();
        AccountApprovedEvent event = new AccountApprovedEvent(
                accountId, userId, "FR7630006000011234567890189", "CHECKING");

        producer.publishAccountApproved(event);

        verify(kafkaTemplate).send(
                eq(KafkaTopicConfig.TOPIC_ACCOUNT_APPROVED),
                eq(accountId.toString()),
                startsWith("{"));
    }

    // ── publishAccountRejected ─────────────────────────────────────────────────

    @Test
    void publishAccountRejected_shouldSendToCorrectTopic() {
        UUID accountId = UUID.randomUUID();
        UUID userId    = UUID.randomUUID();
        AccountRejectedEvent event = new AccountRejectedEvent(
                accountId, userId, "FR7630006000011234567890189", "SAVINGS");

        producer.publishAccountRejected(event);

        verify(kafkaTemplate).send(
                eq(KafkaTopicConfig.TOPIC_ACCOUNT_REJECTED),
                eq(accountId.toString()),
                startsWith("{"));
    }

    // ── Serialization failure ──────────────────────────────────────────────────

    @Test
    void publishDebitResult_shouldThrowRuntimeException_whenSerializationFails() throws Exception {
        doThrow(new JsonProcessingException("serialize error") {})
                .when(objectMapper).writeValueAsString(any());

        DebitResultEvent event = DebitResultEvent.builder()
                .transactionId(transactionId)
                .success(true)
                .build();

        assertThatThrownBy(() -> producer.publishDebitResult(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Kafka serialization error");
    }
}
