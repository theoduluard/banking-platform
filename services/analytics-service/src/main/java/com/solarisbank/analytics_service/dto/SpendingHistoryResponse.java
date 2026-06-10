package com.solarisbank.analytics_service.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class SpendingHistoryResponse {

    private List<HistoryEntry> history;

    @Data
    @Builder
    public static class HistoryEntry {
        private int year;
        private int month;
        private BigDecimal total_debit;
        private BigDecimal total_credit;
        private int transaction_count;
    }
}
