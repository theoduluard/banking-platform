package com.solarisbank.fraud_service.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solarisbank.fraud_service.model.FraudAlert;
import com.solarisbank.fraud_service.repository.FraudAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionFraudConsumer {

    private static final BigDecimal HIGH_AMOUNT_THRESHOLD = new BigDecimal("10000");

    private final FraudAlertRepository repo;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "transaction-events", groupId = "fraud-service")
    @Transactional
    public void consume(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String type = node.path("type").asText("");
            if (!"TRANSFER_COMPLETED".equals(type) && !"DEBIT".equals(type)) return;

            BigDecimal amount = new BigDecimal(node.path("amount").asText("0"));
            UUID transactionId = UUID.fromString(node.path("transactionId").asText(UUID.randomUUID().toString()));
            UUID userId    = UUID.fromString(node.path("userId").asText());
            UUID accountId = UUID.fromString(node.path("accountId").asText());

            String ruleTriggered = null;
            short riskScore = 0;

            // Rule 1: High amount
            if (amount.compareTo(HIGH_AMOUNT_THRESHOLD) > 0) {
                ruleTriggered = "HIGH_AMOUNT";
                riskScore = 75;
            }

            // Rule 2: Round number > 5000 (structuring indicator)
            if (amount.compareTo(new BigDecimal("5000")) > 0
                    && amount.remainder(new BigDecimal("1000")).compareTo(BigDecimal.ZERO) == 0) {
                ruleTriggered = ruleTriggered != null ? ruleTriggered + ",ROUND_AMOUNT" : "ROUND_AMOUNT";
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
                log.warn("Fraud alert created for transaction {} — rules: {}, score: {}", transactionId, ruleTriggered, riskScore);
            }
        } catch (Exception e) {
            log.warn("Failed to process transaction for fraud detection: {}", e.getMessage());
        }
    }
}
