package com.solarisbank.analytics_service.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class MonthlySpendingResponse {

    private int year;
    private int month;
    private List<CategorySummary> categories;
    private BigDecimal total_debit;
    private BigDecimal total_credit;

    @Data
    @Builder
    public static class CategorySummary {
        private String category;
        private BigDecimal total_debit;
        private BigDecimal total_credit;
        private int transaction_count;
    }
}
