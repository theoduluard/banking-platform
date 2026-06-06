package com.solarisbank.loan_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter @Setter
public class LoanSimulationRequest {
    @NotNull @DecimalMin("100") private BigDecimal amount;
    @NotNull @Min(3) @Max(360) private Integer durationMonths;
}
