package com.solarisbank.loan_service.service;

import com.solarisbank.loan_service.dto.*;
import com.solarisbank.loan_service.model.Loan;
import com.solarisbank.loan_service.repository.LoanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoanService {

    // Fixed rate for simplicity — in production this would be dynamic
    private static final BigDecimal ANNUAL_RATE = new BigDecimal("0.055"); // 5.5 %

    private final LoanRepository loanRepository;

    public LoanSimulationResponse simulate(BigDecimal amount, int durationMonths) {
        BigDecimal monthlyRate = ANNUAL_RATE.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
        BigDecimal monthly = computeMonthlyPayment(amount, monthlyRate, durationMonths);
        BigDecimal total   = monthly.multiply(BigDecimal.valueOf(durationMonths)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal interest = total.subtract(amount).setScale(2, RoundingMode.HALF_UP);
        return LoanSimulationResponse.builder()
                .amount(amount).durationMonths(durationMonths)
                .interestRate(ANNUAL_RATE.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP))
                .monthlyPayment(monthly).totalRepayment(total).totalInterest(interest)
                .build();
    }

    @Transactional
    public Loan apply(UUID userId, LoanApplicationRequest req) {
        LoanSimulationResponse sim = simulate(req.getAmount(), req.getDurationMonths());
        Loan loan = Loan.builder()
                .userId(userId)
                .accountId(req.getAccountId())
                .amount(req.getAmount())
                .interestRate(sim.getInterestRate())
                .durationMonths(req.getDurationMonths())
                .monthlyPayment(sim.getMonthlyPayment())
                .totalRepayment(sim.getTotalRepayment())
                .status(Loan.LoanStatus.PENDING)
                .purpose(req.getPurpose())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return loanRepository.save(loan);
    }

    public List<Loan> getUserLoans(UUID userId) {
        return loanRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    // Admin operations
    public List<Loan> getPendingLoans() {
        return loanRepository.findByStatusOrderByCreatedAtDesc(Loan.LoanStatus.PENDING);
    }

    @Transactional
    public Loan approveOrReject(UUID loanId, boolean approve, String adminNote) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found"));
        loan.setStatus(approve ? Loan.LoanStatus.APPROVED : Loan.LoanStatus.REJECTED);
        loan.setAdminNote(adminNote);
        loan.setUpdatedAt(LocalDateTime.now());
        if (approve) loan.setDisbursedAt(LocalDateTime.now());
        return loanRepository.save(loan);
    }

    private BigDecimal computeMonthlyPayment(BigDecimal principal, BigDecimal monthlyRate, int months) {
        // M = P * r * (1+r)^n / ((1+r)^n - 1)
        BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
        BigDecimal pow = onePlusR.pow(months, new MathContext(15, RoundingMode.HALF_UP));
        BigDecimal numerator   = principal.multiply(monthlyRate).multiply(pow);
        BigDecimal denominator = pow.subtract(BigDecimal.ONE);
        return numerator.divide(denominator, 2, RoundingMode.HALF_UP);
    }
}
