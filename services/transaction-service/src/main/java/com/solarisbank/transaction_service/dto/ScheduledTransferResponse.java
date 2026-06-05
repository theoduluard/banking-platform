package com.solarisbank.transaction_service.dto;

import com.solarisbank.transaction_service.model.ScheduledTransfer;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ScheduledTransferResponse {
    private UUID id;
    private UUID fromAccountId;
    private UUID toAccountId;
    private BigDecimal amount;
    private String currency;
    private String description;
    private ScheduledTransfer.Frequency frequency;
    private LocalDate nextExecutionDate;
    private boolean active;
    private LocalDateTime createdAt;
}
