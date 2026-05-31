package com.solarisbank.messaging_service.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solarisbank.messaging_service.kafka.config.KafkaTopics;
import com.solarisbank.messaging_service.kafka.event.AccountStatusChangedEvent;
import com.solarisbank.messaging_service.model.Message;
import com.solarisbank.messaging_service.service.MessagingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountEventConsumer {

    private final MessagingService messagingService;
    private final ObjectMapper     objectMapper;

    @KafkaListener(topics = {KafkaTopics.ACCOUNT_APPROVED, KafkaTopics.ACCOUNT_REJECTED},
                   groupId = "messaging-service")
    public void onAccountStatusChanged(
            String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            AccountStatusChangedEvent event = objectMapper.readValue(payload, AccountStatusChangedEvent.class);
            String accountLabel = "CHECKING".equals(event.getAccountType()) ? "courant" : "épargne";

            if (KafkaTopics.ACCOUNT_APPROVED.equals(topic)) {
                log.info("Account approved for user {}, sending notification", event.getUserId());
                messagingService.sendSystemMessage(
                        event.getUserId(),
                        "Votre compte a été activé",
                        "Votre compte " + accountLabel + " (" + event.getIban() + ") a été vérifié et activé. "
                                + "Vous pouvez désormais effectuer des virements et utiliser toutes les fonctionnalités de votre compte.",
                        Message.Type.APPROVAL
                );
            } else if (KafkaTopics.ACCOUNT_REJECTED.equals(topic)) {
                log.info("Account rejected for user {}, sending notification", event.getUserId());
                messagingService.sendSystemMessage(
                        event.getUserId(),
                        "Votre demande n'a pas été approuvée",
                        "Votre demande d'ouverture de compte " + accountLabel + " (" + event.getIban() + ") n'a pas pu être approuvée. "
                                + "Si vous pensez qu'il s'agit d'une erreur, n'hésitez pas à nous contacter via l'espace Demandes.",
                        Message.Type.REJECTION
                );
            }
        } catch (Exception e) {
            log.error("Failed to process account status event from topic {}: {}", topic, e.getMessage(), e);
        }
    }
}
