package com.solarisbank.account_service.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.solarisbank.account_service.kafka.config.KafkaTopicConfig;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
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
}
