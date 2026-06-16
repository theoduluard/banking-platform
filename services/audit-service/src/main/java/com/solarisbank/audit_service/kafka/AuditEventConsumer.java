package com.solarisbank.audit_service.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.solarisbank.audit_service.model.AuditEvent;
import com.solarisbank.audit_service.repository.AuditEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persists all incoming Kafka events as immutable audit log entries.
 *
 * <p>Transaction events come from the {@code transaction.completed} topic
 * ({@code TransactionCompletedEvent}). Auth events come from {@code auth-events}
 * if the auth-service ever publishes there.
 *
 * <p>For {@code TransactionCompletedEvent} the userId is carried in
 * {@code senderUserId}; for legacy / other event shapes it may be in {@code userId}.
 * This consumer tries both, preferring {@code userId} for backward compatibility.
 */
@Component
@Slf4j
public class AuditEventConsumer {

    private final AuditEventRepository repo;
    // ObjectMapper created directly — Spring Boot 4 registers tools.jackson (Jackson 3.x)
    // as its ObjectMapper bean, not com.fasterxml.jackson, so constructor injection fails.
    private final ObjectMapper objectMapper;

    @Autowired
    public AuditEventConsumer(AuditEventRepository repo) {
        this.repo = repo;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /**
     * Listens on the real transaction-completed topic.
     * The event shape is {@code TransactionCompletedEvent} (fields: transactionId,
     * senderUserId, recipientUserId, fromAccountId, toAccountId, amount, …).
     */
    @KafkaListener(topics = "transaction.completed", groupId = "audit-service")
    @Transactional
    public void consumeTransactions(String message) {
        persist(message, "transaction-service", "TRANSACTION");
    }

    /**
     * Listens on auth-events if the auth-service ever publishes there.
     * Currently auth-service uses email only, so this listener is idle.
     */
    @KafkaListener(topics = "auth-events", groupId = "audit-service")
    @Transactional
    public void consumeAuthEvents(String message) {
        persist(message, "auth-service", "AUTH");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void persist(String message, String source, String entityType) {
        if (message == null || message.isBlank()) {
            log.warn("[AuditService] Received blank message from {} — skipping", source);
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(message);

            String eventType   = node.path("type").asText("UNKNOWN");

            // userId: try "userId" first (legacy / auth events), then "senderUserId"
            // (TransactionCompletedEvent from transaction-service).
            String userIdStr = node.path("userId").asText(null);
            if (userIdStr == null || userIdStr.isBlank()) {
                userIdStr = node.path("senderUserId").asText(null);
            }

            // entityId: try "id" first, then "transactionId"
            String entityIdStr = node.path("id").asText(null);
            if (entityIdStr == null || entityIdStr.isBlank()) {
                entityIdStr = node.path("transactionId").asText(null);
            }

            AuditEvent event = AuditEvent.builder()
                    .eventType(eventType)
                    .source(source)
                    .userId(userIdStr   != null ? tryParseUUID(userIdStr)   : null)
                    .entityType(entityType)
                    .entityId(entityIdStr != null ? tryParseUUID(entityIdStr) : null)
                    .payload(message)
                    .createdAt(LocalDateTime.now())
                    .build();
            repo.save(event);

        } catch (Exception e) {
            log.warn("[AuditService] Failed to persist audit event from {}: {}", source, e.getMessage());
        }
    }

    private UUID tryParseUUID(String s) {
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }
}
