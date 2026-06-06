package com.solarisbank.analytics_service.controller;

import com.solarisbank.analytics_service.model.SpendingAggregate;
import com.solarisbank.analytics_service.repository.SpendingAggregateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final SpendingAggregateRepository repo;

    @GetMapping("/spending/monthly")
    public ResponseEntity<List<SpendingAggregate>> getMonthlySpending(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "0") int month) {
        short y = year  > 0 ? (short) year  : (short) LocalDate.now().getYear();
        short m = month > 0 ? (short) month : (short) LocalDate.now().getMonthValue();
        return ResponseEntity.ok(repo.findByUserIdAndYearAndMonthOrderByCategoryAsc(userId, y, m));
    }

    @GetMapping("/spending/history")
    public ResponseEntity<List<SpendingAggregate>> getSpendingHistory(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(repo.findByUserIdOrderByYearDescMonthDesc(userId));
    }
}
