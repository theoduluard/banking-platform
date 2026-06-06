package com.solarisbank.analytics_service.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.solarisbank.analytics_service.model.SpendingAggregate;
import com.solarisbank.analytics_service.repository.SpendingAggregateRepository;
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
public class TransactionEventConsumer {

    private final SpendingAggregateRepository repo;
    // Instantiated directly — Spring Boot 4 auto-configures tools.jackson (Jackson 3.x)
    // as its default ObjectMapper bean; injecting com.fasterxml.jackson ObjectMapper
    // via constructor would require an explicit @Bean, so we create it inline instead.
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @KafkaListener(topics = "transaction-events", groupId = "analytics-service")
    @Transactional
    public void consume(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String type = node.path("type").asText("");
            if (!"TRANSFER_COMPLETED".equals(type) && !"DEBIT".equals(type) && !"CREDIT".equals(type)) return;

            UUID userId    = UUID.fromString(node.path("userId").asText());
            UUID accountId = UUID.fromString(node.path("accountId").asText());
            BigDecimal amount = new BigDecimal(node.path("amount").asText("0"));
            boolean isDebit = "DEBIT".equals(type) || "TRANSFER_COMPLETED".equals(type);
            LocalDateTime ts = LocalDateTime.now();
            short year  = (short) ts.getYear();
            short month = (short) ts.getMonthValue();
            String category = node.path("category").asText("OTHER");

            SpendingAggregate agg = repo
                    .findByUserIdAndAccountIdAndYearAndMonthAndCategory(userId, accountId, year, month, category)
                    .orElseGet(() -> SpendingAggregate.builder()
                            .userId(userId).accountId(accountId)
                            .year(year).month(month).category(category)
                            .totalDebit(BigDecimal.ZERO).totalCredit(BigDecimal.ZERO)
                            .txCount(0).updatedAt(ts).build());

            if (isDebit) agg.setTotalDebit(agg.getTotalDebit().add(amount));
            else         agg.setTotalCredit(agg.getTotalCredit().add(amount));
            agg.setTxCount(agg.getTxCount() + 1);
            agg.setUpdatedAt(ts);
            repo.save(agg);
        } catch (Exception e) {
            log.warn("Failed to process transaction event for analytics: {}", e.getMessage());
        }
    }
}
