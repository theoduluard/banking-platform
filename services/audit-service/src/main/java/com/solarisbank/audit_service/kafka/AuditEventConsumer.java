package com.solarisbank.audit_service.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solarisbank.audit_service.model.AuditEvent;
import com.solarisbank.audit_service.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditEventConsumer {

    private final AuditEventRepository repo;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "transaction-events", groupId = "audit-service")
    @Transactional
    public void consumeTransactions(String message) {
        persist(message, "transaction-service", "TRANSACTION");
    }

    @KafkaListener(topics = "auth-events", groupId = "audit-service")
    @Transactional
    public void consumeAuthEvents(String message) {
        persist(message, "auth-service", "AUTH");
    }

    private void persist(String message, String source, String entityType) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String eventType = node.path("type").asText("UNKNOWN");
            String userIdStr = node.path("userId").asText(null);
            String entityIdStr = node.path("id").asText(node.path("transactionId").asText(null));

            AuditEvent event = AuditEvent.builder()
                    .eventType(eventType)
                    .source(source)
                    .userId(userIdStr != null ? tryParseUUID(userIdStr) : null)
                    .entityType(entityType)
                    .entityId(entityIdStr != null ? tryParseUUID(entityIdStr) : null)
                    .payload(message)
                    .createdAt(LocalDateTime.now())
                    .build();
            repo.save(event);
        } catch (Exception e) {
            log.warn("Failed to persist audit event from {}: {}", source, e.getMessage());
        }
    }

    private UUID tryParseUUID(String s) {
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }
}
