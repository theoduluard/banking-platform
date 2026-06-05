package com.solarisbank.notification_service.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solarisbank.notification_service.kafka.config.KafkaTopicConfig;
import com.solarisbank.notification_service.kafka.event.TransactionCompletedEvent;
import com.solarisbank.notification_service.kafka.event.TransactionFailedEvent;
import com.solarisbank.notification_service.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopicConfig.TOPIC_TRANSACTION_COMPLETED,
                   groupId = "notification-service")
    public void onTransactionCompleted(String payload) {
        TransactionCompletedEvent event;
        try {
            event = objectMapper.readValue(payload, TransactionCompletedEvent.class);
        } catch (Exception e) {
            // Poison-pill: unparseable message — log and skip (no retry)
            log.error("Unparseable TransactionCompletedEvent, skipping: {}", payload, e);
            return;
        }
        log.info("[Consumer] Received TransactionCompletedEvent for transaction id={}",
                event.getTransactionId());
        notificationService.handleTransactionCompleted(event);
    }

    @KafkaListener(topics = KafkaTopicConfig.TOPIC_TRANSACTION_FAILED,
                   groupId = "notification-service")
    public void onTransactionFailed(String payload) {
        TransactionFailedEvent event;
        try {
            event = objectMapper.readValue(payload, TransactionFailedEvent.class);
        } catch (Exception e) {
            log.error("Unparseable TransactionFailedEvent, skipping: {}", payload, e);
            return;
        }
        log.info("[Consumer] Received TransactionFailedEvent for transaction id={}",
                event.getTransactionId());
        notificationService.handleTransactionFailed(event);
    }
}
