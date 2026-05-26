package com.solarisbank.transaction_service.dto;

import com.solarisbank.transaction_service.model.Transaction;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TransactionResponse {
    private UUID id;
    private UUID fromAccountId;
    private UUID toAccountId;
    private BigDecimal amount;
    private String currency;
    private Transaction.Type type;
    private Transaction.Status status;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
