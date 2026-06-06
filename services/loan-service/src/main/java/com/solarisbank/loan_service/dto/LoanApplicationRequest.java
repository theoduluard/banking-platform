package com.solarisbank.loan_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

@Getter @Setter
public class LoanApplicationRequest {
    @NotNull private UUID accountId;
    @NotNull @DecimalMin("100") private BigDecimal amount;
    @NotNull @Min(3) @Max(360) private Integer durationMonths;
    private String purpose;
}
