package com.solarisbank.messaging_service.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solarisbank.messaging_service.kafka.config.KafkaTopics;
import com.solarisbank.messaging_service.kafka.event.AccountStatusChangedEvent;
import com.solarisbank.messaging_service.model.Message;
import com.solarisbank.messaging_service.service.MessagingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountEventConsumerTest {

    @Mock
    private MessagingService messagingService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AccountEventConsumer accountEventConsumer;

    private UUID userId;
    private UUID accountId;
    private AccountStatusChangedEvent approvedEvent;
    private AccountStatusChangedEvent rejectedEvent;

    @BeforeEach
    void setUp() throws Exception {
        userId    = UUID.randomUUID();
        accountId = UUID.randomUUID();

        approvedEvent = new AccountStatusChangedEvent();
        approvedEvent.setUserId(userId);
        approvedEvent.setAccountId(accountId);
        approvedEvent.setIban("FR7630006000010000000000197");
        approvedEvent.setAccountType("CHECKING");

        rejectedEvent = new AccountStatusChangedEvent();
        rejectedEvent.setUserId(userId);
        rejectedEvent.setAccountId(accountId);
        rejectedEvent.setIban("FR7630006000010000000000197");
        rejectedEvent.setAccountType("SAVINGS");
    }

    // ── ACCOUNT_APPROVED topic ─────────────────────────────────────────────────

    @Test
    void onAccountStatusChanged_shouldSendApprovalMessage_whenAccountApproved() throws Exception {
        String payload = "{\"userId\":\"" + userId + "\",\"accountId\":\"" + accountId
                + "\",\"iban\":\"FR7630006000010000000000197\",\"accountType\":\"CHECKING\"}";
        when(objectMapper.readValue(payload, AccountStatusChangedEvent.class))
                .thenReturn(approvedEvent);

        accountEventConsumer.onAccountStatusChanged(payload, KafkaTopics.ACCOUNT_APPROVED);

        verify(messagingService).sendSystemMessage(
                eq(userId),
                eq("Votre compte a été activé"),
                contains("FR7630006000010000000000197"),
                eq(Message.Type.APPROVAL)
        );
    }

    @Test
    void onAccountStatusChanged_shouldUseSavingsLabel_whenAccountTypeIsSavings() throws Exception {
        String payload = "{}";
        AccountStatusChangedEvent savingsEvent = new AccountStatusChangedEvent();
        savingsEvent.setUserId(userId);
        savingsEvent.setAccountId(accountId);
        savingsEvent.setIban("FR7630006000019876543210197");
        savingsEvent.setAccountType("SAVINGS");

        when(objectMapper.readValue(payload, AccountStatusChangedEvent.class))
                .thenReturn(savingsEvent);

        accountEventConsumer.onAccountStatusChanged(payload, KafkaTopics.ACCOUNT_APPROVED);

        verify(messagingService).sendSystemMessage(
                eq(userId),
                eq("Votre compte a été activé"),
                contains("épargne"),
                eq(Message.Type.APPROVAL)
        );
    }

    // ── ACCOUNT_REJECTED topic ─────────────────────────────────────────────────

    @Test
    void onAccountStatusChanged_shouldSendRejectionMessage_whenAccountRejected() throws Exception {
        String payload = "{}";
        when(objectMapper.readValue(payload, AccountStatusChangedEvent.class))
                .thenReturn(rejectedEvent);

        accountEventConsumer.onAccountStatusChanged(payload, KafkaTopics.ACCOUNT_REJECTED);

        verify(messagingService).sendSystemMessage(
                eq(userId),
                eq("Votre demande n'a pas été approuvée"),
                contains("FR7630006000010000000000197"),
                eq(Message.Type.REJECTION)
        );
    }

    @Test
    void onAccountStatusChanged_shouldSendRejectionWithCheckingLabel_whenCheckingRejected()
            throws Exception {
        String payload = "{}";
        AccountStatusChangedEvent checkingRejected = new AccountStatusChangedEvent();
        checkingRejected.setUserId(userId);
        checkingRejected.setAccountId(accountId);
        checkingRejected.setIban("FR7630006000010000000000197");
        checkingRejected.setAccountType("CHECKING");

        when(objectMapper.readValue(payload, AccountStatusChangedEvent.class))
                .thenReturn(checkingRejected);

        accountEventConsumer.onAccountStatusChanged(payload, KafkaTopics.ACCOUNT_REJECTED);

        verify(messagingService).sendSystemMessage(
                eq(userId),
                eq("Votre demande n'a pas été approuvée"),
                contains("courant"),
                eq(Message.Type.REJECTION)
        );
    }

    // ── Error handling ─────────────────────────────────────────────────────────

    @Test
    void onAccountStatusChanged_shouldNotThrow_whenPayloadIsInvalid() throws Exception {
        String invalidPayload = "not-valid-json";
        when(objectMapper.readValue(invalidPayload, AccountStatusChangedEvent.class))
                .thenThrow(new com.fasterxml.jackson.core.JsonParseException(null, "bad json"));

        // Must not propagate — just log and swallow
        accountEventConsumer.onAccountStatusChanged(invalidPayload, KafkaTopics.ACCOUNT_APPROVED);

        verifyNoInteractions(messagingService);
    }

    @Test
    void onAccountStatusChanged_shouldDoNothing_whenTopicIsUnknown() throws Exception {
        String payload = "{}";
        when(objectMapper.readValue(payload, AccountStatusChangedEvent.class))
                .thenReturn(approvedEvent);

        accountEventConsumer.onAccountStatusChanged(payload, "unknown-topic");

        // Neither approved nor rejected branch should fire
        verifyNoInteractions(messagingService);
    }
}
