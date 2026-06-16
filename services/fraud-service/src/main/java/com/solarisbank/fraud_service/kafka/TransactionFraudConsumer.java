package com.solarisbank.fraud_service.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.solarisbank.fraud_service.model.FraudAlert;
import com.solarisbank.fraud_service.repository.FraudAlertRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Consumes {@code transaction.completed} events and applies fraud-detection rules.
 *
 * <p>Each event is a completed debit-credit saga ({@code TransactionCompletedEvent}).
 * Fields expected:
 * <ul>
 *   <li>{@code transactionId}  — UUID of the completed transaction</li>
 *   <li>{@code senderUserId}   — UUID of the user who initiated the transfer</li>
 *   <li>{@code fromAccountId}  — UUID of the debit account</li>
 *   <li>{@code amount}         — transfer amount (string representation of BigDecimal)</li>
 * </ul>
 *
 * <p>Rules applied:
 * <ol>
 *   <li>HIGH_AMOUNT  — amount > 10 000 € → risk score 75</li>
 *   <li>ROUND_AMOUNT — amount > 5 000 € and divisible by 1 000 (structuring indicator)
 *       → risk score +20 (capped at 100)</li>
 * </ol>
 */
@Component
@Slf4j
public class TransactionFraudConsumer {

    private static final BigDecimal HIGH_AMOUNT_THRESHOLD  = new BigDecimal("10000");
    private static final BigDecimal ROUND_AMOUNT_THRESHOLD = new BigDecimal("5000");
    private static final BigDecimal ROUND_DIVISOR          = new BigDecimal("1000");

    private final FraudAlertRepository repo;
    // ObjectMapper created directly — Spring Boot 4 registers tools.jackson (Jackson 3.x)
    // as its ObjectMapper bean, not com.fasterxml.jackson, so constructor injection fails.
    private final ObjectMapper objectMapper;

    @Autowired
    public TransactionFraudConsumer(FraudAlertRepository repo) {
        this.repo = repo;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @KafkaListener(topics = "transaction.completed", groupId = "fraud-service")
    @Transactional
    public void consume(String message) {
        if (message == null || message.isBlank()) return;
        try {
            JsonNode node = objectMapper.readTree(message);

            // ── Parse required fields ──────────────────────────────────────────
            String amountStr  = node.path("amount").asText(null);
            String txIdStr    = node.path("transactionId").asText(null);
            String userIdStr  = node.path("senderUserId").asText(null);
            String accountStr = node.path("fromAccountId").asText(null);

            if (amountStr == null || txIdStr == null || userIdStr == null || accountStr == null) {
                log.warn("[FraudService] Incomplete event — missing required fields, skipping");
                return;
            }

            BigDecimal amount        = new BigDecimal(amountStr);
            UUID       transactionId = UUID.fromString(txIdStr);
            UUID       userId        = UUID.fromString(userIdStr);
            UUID       accountId     = UUID.fromString(accountStr);

            // ── Apply rules ────────────────────────────────────────────────────
            String ruleTriggered = null;
            short  riskScore     = 0;

            // Rule 1: High amount (> 10 000 €)
            if (amount.compareTo(HIGH_AMOUNT_THRESHOLD) > 0) {
                ruleTriggered = "HIGH_AMOUNT";
                riskScore     = 75;
            }

            // Rule 2: Round number > 5 000 € (structuring indicator)
            if (amount.compareTo(ROUND_AMOUNT_THRESHOLD) > 0
                    && amount.remainder(ROUND_DIVISOR).compareTo(BigDecimal.ZERO) == 0) {
                ruleTriggered = ruleTriggered != null
                        ? ruleTriggered + ",ROUND_AMOUNT"
                        : "ROUND_AMOUNT";
                riskScore = (short) Math.min(100, riskScore + 20);
            }

            if (ruleTriggered != null) {
                FraudAlert alert = FraudAlert.builder()
                        .transactionId(transactionId)
                        .userId(userId)
                        .accountId(accountId)
                        .amount(amount)
                        .ruleTriggered(ruleTriggered)
                        .riskScore(riskScore)
                        .status(FraudAlert.AlertStatus.OPEN)
                        .createdAt(LocalDateTime.now())
                        .build();
                repo.save(alert);
                log.warn("[FraudService] Alert created for tx {} — rules: {}, score: {}",
                        transactionId, ruleTriggered, riskScore);
            }

        } catch (Exception e) {
            log.warn("[FraudService] Failed to process event: {}", e.getMessage());
        }
    }
}
