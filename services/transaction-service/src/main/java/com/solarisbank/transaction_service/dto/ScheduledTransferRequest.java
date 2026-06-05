package com.solarisbank.transaction_service.dto;

import com.solarisbank.transaction_service.model.ScheduledTransfer;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class ScheduledTransferRequest {

    @NotNull(message = "Source account is required")
    private UUID fromAccountId;

    @NotNull(message = "Destination account is required")
    private UUID toAccountId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    private BigDecimal amount;

    private String description;

    @NotNull(message = "Frequency is required")
    private ScheduledTransfer.Frequency frequency;

    @NotNull(message = "First execution date is required")
    @FutureOrPresent(message = "First execution date must be today or in the future")
    private LocalDate firstExecutionDate;
}
