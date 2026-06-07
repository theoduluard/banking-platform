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

    private SpendingAggregate buildAggregate(String category, String debit) {
        return SpendingAggregate.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .accountId(UUID.randomUUID())
                .year((short) LocalDate.now().getYear())
                .month((short) LocalDate.now().getMonthValue())
                .category(category)
                .totalDebit(new BigDecimal(debit))
                .totalCredit(BigDecimal.ZERO)
                .txCount(3)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ── GET /spending/monthly ──────────────────────────────────────────────────

    @Test
    void getMonthlySpending_withExplicitYearAndMonth_shouldReturn200WithData() throws Exception {
        when(repo.findByUserIdAndYearAndMonthOrderByCategoryAsc(eq(USER_ID), eq((short) 2025), eq((short) 6)))
                .thenReturn(List.of(buildAggregate("GROCERIES", "350.00")));

        mockMvc.perform(get("/api/v1/analytics/spending/monthly")
                        .header("X-User-Id", USER_ID.toString())
                        .param("year", "2025")
                        .param("month", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("GROCERIES"))
                .andExpect(jsonPath("$[0].totalDebit").value(350.00));

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
                .andExpect(jsonPath("$[0].category").value("TRANSPORT"));

        verify(repo).findByUserIdAndYearAndMonthOrderByCategoryAsc(USER_ID, currentYear, currentMonth);
    }

    @Test
    void getMonthlySpending_emptyResult_shouldReturn200EmptyArray() throws Exception {
        when(repo.findByUserIdAndYearAndMonthOrderByCategoryAsc(any(), anyShort(), anyShort()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/analytics/spending/monthly")
                        .header("X-User-Id", USER_ID.toString())
                        .param("year", "2024")
                        .param("month", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── GET /spending/history ──────────────────────────────────────────────────

    @Test
    void getSpendingHistory_shouldReturn200WithAllAggregates() throws Exception {
        when(repo.findByUserIdOrderByYearDescMonthDesc(USER_ID))
                .thenReturn(List.of(
                        buildAggregate("GROCERIES", "400.00"),
                        buildAggregate("TRANSPORT", "90.00")));

        mockMvc.perform(get("/api/v1/analytics/spending/history")
                        .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        verify(repo).findByUserIdOrderByYearDescMonthDesc(USER_ID);
    }

    @Test
    void getSpendingHistory_empty_shouldReturn200EmptyArray() throws Exception {
        when(repo.findByUserIdOrderByYearDescMonthDesc(USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/analytics/spending/history")
                        .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
