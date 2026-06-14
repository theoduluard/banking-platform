package com.solarisbank.analytics_service.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.solarisbank.analytics_service.model.SpendingAggregate;
import com.solarisbank.analytics_service.repository.SpendingAggregateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Consumes "transaction.completed" events published by transaction-service
 * and aggregates debit/credit amounts per user, account, month and category.
 *
 * Each completed transfer produces two aggregate entries:
 *   - DEBIT  for the sender   (senderUserId    / fromAccountId)
 *   - CREDIT for the recipient (recipientUserId / toAccountId)
 *
 * Event shape (TransactionCompletedEvent):
 * {
 *   "transactionId": "...",
 *   "fromAccountId": "...",
 *   "toAccountId": "...",
 *   "senderUserId": "...",
 *   "recipientUserId": "...",
 *   "amount": "100.00",
 *   "currency": "EUR",
 *   "description": "...",
 *   "completedAt": "..."
 * }
 */
@Component
@Slf4j
public class TransactionEventConsumer {

    private static final String CATEGORY_TRANSFER = "Virement";

    private final SpendingAggregateRepository repo;
    private final ObjectMapper objectMapper;

    @Autowired
    public TransactionEventConsumer(SpendingAggregateRepository repo) {
        this.repo = repo;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @KafkaListener(topics = "transaction.completed", groupId = "analytics-service")
    @Transactional
    public void consume(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);

            String senderUserIdStr    = node.path("senderUserId").asText(null);
            String recipientUserIdStr = node.path("recipientUserId").asText(null);
            String fromAccountIdStr   = node.path("fromAccountId").asText(null);
            String toAccountIdStr     = node.path("toAccountId").asText(null);
            String amountStr          = node.path("amount").asText("0");

            if (senderUserIdStr == null || fromAccountIdStr == null) {
                log.warn("[Analytics] Skipping event — missing senderUserId or fromAccountId: {}", message);
                return;
            }

            UUID senderUserId  = UUID.fromString(senderUserIdStr);
            UUID fromAccountId = UUID.fromString(fromAccountIdStr);
            BigDecimal amount  = new BigDecimal(amountStr);

            // Use completedAt from the event if present, otherwise now()
            LocalDateTime ts = LocalDateTime.now();
            if (!node.path("completedAt").isMissingNode()) {
                try {
                    ts = objectMapper.treeToValue(node.path("completedAt"), LocalDateTime.class);
                } catch (Exception ignored) { /* fallback to now() */ }
            }

            short year  = (short) ts.getYear();
            short month = (short) ts.getMonthValue();

            // ── Debit entry for the sender ────────────────────────────────────
            upsertAggregate(senderUserId, fromAccountId, year, month, CATEGORY_TRANSFER,
                    amount, BigDecimal.ZERO, ts);

            // ── Credit entry for the recipient ────────────────────────────────
            if (recipientUserIdStr != null && toAccountIdStr != null) {
                UUID recipientUserId = UUID.fromString(recipientUserIdStr);
                UUID toAccountId     = UUID.fromString(toAccountIdStr);
                upsertAggregate(recipientUserId, toAccountId, year, month, CATEGORY_TRANSFER,
                        BigDecimal.ZERO, amount, ts);
            }

        } catch (Exception e) {
            log.warn("[Analytics] Failed to process transaction.completed event: {}", e.getMessage());
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private void upsertAggregate(UUID userId, UUID accountId,
                                  short year, short month, String category,
                                  BigDecimal debit, BigDecimal credit,
                                  LocalDateTime updatedAt) {
        SpendingAggregate agg = repo
                .findByUserIdAndAccountIdAndYearAndMonthAndCategory(userId, accountId, year, month, category)
                .orElseGet(() -> SpendingAggregate.builder()
                        .userId(userId).accountId(accountId)
                        .year(year).month(month).category(category)
                        .totalDebit(BigDecimal.ZERO).totalCredit(BigDecimal.ZERO)
                        .txCount(0).updatedAt(updatedAt).build());

        agg.setTotalDebit(agg.getTotalDebit().add(debit));
        agg.setTotalCredit(agg.getTotalCredit().add(credit));
        agg.setTxCount(agg.getTxCount() + 1);
        agg.setUpdatedAt(updatedAt);
        repo.save(agg);
    }
}
