package com.solarisbank.account_service.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.solarisbank.account_service.kafka.event.CreditRequestedEvent;
import com.solarisbank.account_service.kafka.event.DebitRequestedEvent;
import com.solarisbank.account_service.kafka.producer.AccountEventProducer;
import com.solarisbank.account_service.repository.ProcessedSagaEventRepository;
import com.solarisbank.account_service.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountCommandConsumerTest {

    @Mock
    private AccountService accountService;

    @Mock
    private AccountEventProducer accountEventProducer;

    @Mock
    private ProcessedSagaEventRepository processedEventRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @InjectMocks
    private AccountCommandConsumer consumer;

    private UUID transactionId;
    private UUID accountId;
    private UUID userId;
    private BigDecimal amount;

    @BeforeEach
    void setUp() {
        transactionId = UUID.randomUUID();
        accountId     = UUID.randomUUID();
        userId        = UUID.randomUUID();
        amount        = new BigDecimal("200.00");
    }

    // ── onDebitRequested ───────────────────────────────────────────────────────

    @Test
    void onDebitRequested_shouldPublishSuccessResult_whenDebitSucceeds() throws Exception {
        DebitRequestedEvent event = new DebitRequestedEvent(transactionId, accountId, userId, amount);
        String payload = objectMapper.writeValueAsString(event);

        // debitFromSaga is a void method — default Mockito behaviour (do-nothing) is sufficient
        consumer.onDebitRequested(payload);

        verify(accountEventProducer).publishDebitResult(argThat(r ->
                r.getTransactionId().equals(transactionId) && r.isSuccess()));
    }

    @Test
    void onDebitRequested_shouldPublishFailureResult_whenDebitThrows() throws Exception {
        DebitRequestedEvent event = new DebitRequestedEvent(transactionId, accountId, userId, amount);
        String payload = objectMapper.writeValueAsString(event);

        doThrow(new RuntimeException("Insufficient funds"))
                .when(accountService).debitFromSaga(eq(transactionId), eq(accountId), eq(userId), eq(amount));

        consumer.onDebitRequested(payload);

        verify(accountEventProducer).publishDebitResult(argThat(r ->
                r.getTransactionId().equals(transactionId)
                && !r.isSuccess()
                && r.getErrorMessage().contains("Insufficient funds")));
    }

    @Test
    void onDebitRequested_shouldNotThrow_whenPayloadIsInvalidJson() {
        // L'exception interne est loggée, le consumer ne doit pas propager l'erreur
        consumer.onDebitRequested("{ invalid json }");

        verifyNoInteractions(accountService, accountEventProducer);
    }

    // ── onCreditRequested ──────────────────────────────────────────────────────

    @Test
    void onCreditRequested_shouldPublishSuccessResult_whenCreditSucceeds() throws Exception {
        UUID toAccountId   = UUID.randomUUID();
        UUID fromAccountId = UUID.randomUUID();
        CreditRequestedEvent event = new CreditRequestedEvent(transactionId, toAccountId, fromAccountId, amount);
        String payload = objectMapper.writeValueAsString(event);

        // creditFromSaga is a void method — default Mockito behaviour (do-nothing) is sufficient
        consumer.onCreditRequested(payload);

        verify(accountEventProducer).publishCreditResult(argThat(r ->
                r.getTransactionId().equals(transactionId) && r.isSuccess()));
    }

    @Test
    void onCreditRequested_shouldPublishFailureResult_whenCreditThrows() throws Exception {
        UUID toAccountId   = UUID.randomUUID();
        UUID fromAccountId = UUID.randomUUID();
        CreditRequestedEvent event = new CreditRequestedEvent(transactionId, toAccountId, fromAccountId, amount);
        String payload = objectMapper.writeValueAsString(event);

        doThrow(new RuntimeException("Account blocked"))
                .when(accountService).creditFromSaga(eq(transactionId), eq(toAccountId), eq(amount));

        consumer.onCreditRequested(payload);

        verify(accountEventProducer).publishCreditResult(argThat(r ->
                r.getTransactionId().equals(transactionId)
                && !r.isSuccess()
                && r.getErrorMessage().contains("Account blocked")));
    }

    @Test
    void onCreditRequested_shouldNotThrow_whenPayloadIsInvalidJson() {
        consumer.onCreditRequested("not-json");

        verifyNoInteractions(accountService, accountEventProducer);
    }
}
