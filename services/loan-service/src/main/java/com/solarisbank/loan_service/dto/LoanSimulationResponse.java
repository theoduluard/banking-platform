package com.solarisbank.loan_service.dto;

import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;

@Getter @Builder
public class LoanSimulationResponse {
    private BigDecimal amount;
    private int durationMonths;
    private BigDecimal interestRate;
    private BigDecimal monthlyPayment;
    private BigDecimal totalRepayment;
    private BigDecimal totalInterest;
}
