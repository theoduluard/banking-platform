package com.solarisbank.analytics_service.controller;

import com.solarisbank.analytics_service.dto.MonthlySpendingResponse;
import com.solarisbank.analytics_service.dto.SpendingHistoryResponse;
import com.solarisbank.analytics_service.model.SpendingAggregate;
import com.solarisbank.analytics_service.repository.SpendingAggregateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final SpendingAggregateRepository repo;

    /**
     * Returns monthly spending aggregated by category.
     * Shape matches the frontend MonthlySpendingResponse type:
     * { year, month, categories: [{category, total_debit, total_credit, transaction_count}],
     *   total_debit, total_credit }
     */
    @GetMapping("/spending/monthly")
    public ResponseEntity<MonthlySpendingResponse> getMonthlySpending(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "0") int month) {

        short y = year  > 0 ? (short) year  : (short) LocalDate.now().getYear();
        short m = month > 0 ? (short) month : (short) LocalDate.now().getMonthValue();

        List<SpendingAggregate> rows =
                repo.findByUserIdAndYearAndMonthOrderByCategoryAsc(userId, y, m);

        List<MonthlySpendingResponse.CategorySummary> categories = rows.stream()
                .map(r -> MonthlySpendingResponse.CategorySummary.builder()
                        .category(r.getCategory())
                        .total_debit(r.getTotalDebit())
                        .total_credit(r.getTotalCredit())
                        .transaction_count(r.getTxCount())
                        .build())
                .toList();

        BigDecimal totalDebit  = rows.stream()
                .map(SpendingAggregate::getTotalDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = rows.stream()
                .map(SpendingAggregate::getTotalCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ResponseEntity.ok(MonthlySpendingResponse.builder()
                .year(y)
                .month(m)
                .categories(categories)
                .total_debit(totalDebit)
                .total_credit(totalCredit)
                .build());
    }

    /**
     * Returns all monthly totals, grouped by (year, month) descending.
     * Shape matches the frontend SpendingHistoryResponse type:
     * { history: [{year, month, total_debit, total_credit, transaction_count}] }
     */
    @GetMapping("/spending/history")
    public ResponseEntity<SpendingHistoryResponse> getSpendingHistory(
            @RequestHeader("X-User-Id") UUID userId) {

        List<SpendingAggregate> rows =
                repo.findByUserIdOrderByYearDescMonthDesc(userId);

        // Group by (year, month) — LinkedHashMap preserves insertion order (already sorted desc)
        Map<String, SpendingHistoryResponse.HistoryEntry> grouped = new LinkedHashMap<>();
        for (SpendingAggregate r : rows) {
            String key = r.getYear() + "-" + r.getMonth();
            grouped.merge(key,
                SpendingHistoryResponse.HistoryEntry.builder()
                        .year(r.getYear())
                        .month(r.getMonth())
                        .total_debit(r.getTotalDebit())
                        .total_credit(r.getTotalCredit())
                        .transaction_count(r.getTxCount())
                        .build(),
                (existing, newEntry) -> SpendingHistoryResponse.HistoryEntry.builder()
                        .year(existing.getYear())
                        .month(existing.getMonth())
                        .total_debit(existing.getTotal_debit().add(newEntry.getTotal_debit()))
                        .total_credit(existing.getTotal_credit().add(newEntry.getTotal_credit()))
                        .transaction_count(existing.getTransaction_count() + newEntry.getTransaction_count())
                        .build()
            );
        }

        return ResponseEntity.ok(SpendingHistoryResponse.builder()
                .history(List.copyOf(grouped.values()))
                .build());
    }
}
