package com.solarisbank.analytics_service.controller;

import com.solarisbank.analytics_service.model.SpendingAggregate;
import com.solarisbank.analytics_service.repository.SpendingAggregateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AnalyticsController.class)
class AnalyticsControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean SpendingAggregateRepository repo;

    private static final UUID USER_ID = UUID.randomUUID();

    private SpendingAggregate buildAggregate(String category, String debit, short year, short month) {
        return SpendingAggregate.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .accountId(UUID.randomUUID())
                .year(year)
                .month(month)
                .category(category)
                .totalDebit(new BigDecimal(debit))
                .totalCredit(BigDecimal.ZERO)
                .txCount(3)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private SpendingAggregate buildAggregate(String category, String debit) {
        return buildAggregate(category, debit,
                (short) LocalDate.now().getYear(),
                (short) LocalDate.now().getMonthValue());
    }

    // ── GET /spending/monthly ──────────────────────────────────────────────────

    @Test
    void getMonthlySpending_withExplicitYearAndMonth_shouldReturn200WithWrappedData() throws Exception {
        when(repo.findByUserIdAndYearAndMonthOrderByCategoryAsc(eq(USER_ID), eq((short) 2025), eq((short) 6)))
                .thenReturn(List.of(buildAggregate("GROCERIES", "350.00", (short) 2025, (short) 6)));

        mockMvc.perform(get("/api/v1/analytics/spending/monthly")
                        .header("X-User-Id", USER_ID.toString())
                        .param("year", "2025")
                        .param("month", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2025))
                .andExpect(jsonPath("$.month").value(6))
                .andExpect(jsonPath("$.categories[0].category").value("GROCERIES"))
                .andExpect(jsonPath("$.categories[0].total_debit").value(350.00))
                .andExpect(jsonPath("$.categories[0].transaction_count").value(3))
                .andExpect(jsonPath("$.total_debit").value(350.00))
                .andExpect(jsonPath("$.total_credit").value(0));

        verify(repo).findByUserIdAndYearAndMonthOrderByCategoryAsc(USER_ID, (short) 2025, (short) 6);
    }

    @Test
    void getMonthlySpending_withoutParams_shouldDefaultToCurrentYearMonth() throws Exception {
        short currentYear  = (short) LocalDate.now().getYear();
        short currentMonth = (short) LocalDate.now().getMonthValue();

        when(repo.findByUserIdAndYearAndMonthOrderByCategoryAsc(eq(USER_ID), eq(currentYear), eq(currentMonth)))
                .thenReturn(List.of(buildAggregate("TRANSPORT", "80.00")));

        mockMvc.perform(get("/api/v1/analytics/spending/monthly")
                        .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value((int) currentYear))
                .andExpect(jsonPath("$.month").value((int) currentMonth))
                .andExpect(jsonPath("$.categories[0].category").value("TRANSPORT"))
                .andExpect(jsonPath("$.total_debit").value(80.00));

        verify(repo).findByUserIdAndYearAndMonthOrderByCategoryAsc(USER_ID, currentYear, currentMonth);
    }

    @Test
    void getMonthlySpending_emptyResult_shouldReturn200WithEmptyCategories() throws Exception {
        when(repo.findByUserIdAndYearAndMonthOrderByCategoryAsc(any(), anyShort(), anyShort()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/analytics/spending/monthly")
                        .header("X-User-Id", USER_ID.toString())
                        .param("year", "2024")
                        .param("month", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2024))
                .andExpect(jsonPath("$.month").value(1))
                .andExpect(jsonPath("$.categories").isEmpty())
                .andExpect(jsonPath("$.total_debit").value(0))
                .andExpect(jsonPath("$.total_credit").value(0));
    }

    @Test
    void getMonthlySpending_multipleCategories_shouldSumTotals() throws Exception {
        when(repo.findByUserIdAndYearAndMonthOrderByCategoryAsc(eq(USER_ID), eq((short) 2026), eq((short) 3)))
                .thenReturn(List.of(
                        buildAggregate("GROCERIES", "200.00", (short) 2026, (short) 3),
                        buildAggregate("TRANSPORT", "100.00", (short) 2026, (short) 3)));

        mockMvc.perform(get("/api/v1/analytics/spending/monthly")
                        .header("X-User-Id", USER_ID.toString())
                        .param("year", "2026")
                        .param("month", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories.length()").value(2))
                .andExpect(jsonPath("$.total_debit").value(300.00));
    }

    // ── GET /spending/history ──────────────────────────────────────────────────

    @Test
    void getSpendingHistory_shouldReturn200WithGroupedMonths() throws Exception {
        // Two aggregates for the same month (different categories) → grouped into 1 entry
        when(repo.findByUserIdOrderByYearDescMonthDesc(USER_ID))
                .thenReturn(List.of(
                        buildAggregate("GROCERIES", "400.00", (short) 2026, (short) 5),
                        buildAggregate("TRANSPORT", "90.00",  (short) 2026, (short) 5)));

        mockMvc.perform(get("/api/v1/analytics/spending/history")
                        .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history.length()").value(1))
                .andExpect(jsonPath("$.history[0].year").value(2026))
                .andExpect(jsonPath("$.history[0].month").value(5))
                .andExpect(jsonPath("$.history[0].total_debit").value(490.00))
                .andExpect(jsonPath("$.history[0].transaction_count").value(6));

        verify(repo).findByUserIdOrderByYearDescMonthDesc(USER_ID);
    }

    @Test
    void getSpendingHistory_twoMonths_shouldReturnTwoEntries() throws Exception {
        when(repo.findByUserIdOrderByYearDescMonthDesc(USER_ID))
                .thenReturn(List.of(
                        buildAggregate("GROCERIES", "400.00", (short) 2026, (short) 5),
                        buildAggregate("GROCERIES", "300.00", (short) 2026, (short) 4)));

        mockMvc.perform(get("/api/v1/analytics/spending/history")
                        .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history.length()").value(2));
    }

    @Test
    void getSpendingHistory_empty_shouldReturn200WithEmptyHistory() throws Exception {
        when(repo.findByUserIdOrderByYearDescMonthDesc(USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/analytics/spending/history")
                        .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history").isEmpty());
    }
}
