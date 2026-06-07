package com.solarisbank.loan_service.service;

import com.solarisbank.loan_service.dto.LoanApplicationRequest;
import com.solarisbank.loan_service.dto.LoanSimulationResponse;
import com.solarisbank.loan_service.model.Loan;
import com.solarisbank.loan_service.repository.LoanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    @Mock LoanRepository loanRepository;
    @InjectMocks LoanService loanService;

    private static final UUID USER_ID    = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID LOAN_ID    = UUID.randomUUID();

    // ── simulate ───────────────────────────────────────────────────────────────

    @Test
    void simulate_shouldReturnCorrectMonthlyPayment() {
        // 10 000 € over 36 months at 5.5% annual
        LoanSimulationResponse resp = loanService.simulate(new BigDecimal("10000"), 36);

        assertThat(resp.getAmount()).isEqualByComparingTo("10000");
        assertThat(resp.getDurationMonths()).isEqualTo(36);
        assertThat(resp.getInterestRate()).isEqualByComparingTo("5.50");
        // Standard loan formula gives ~300.96 €/month
        assertThat(resp.getMonthlyPayment())
                .isCloseTo(new BigDecimal("300.96"), within(new BigDecimal("1.00")));
        assertThat(resp.getTotalRepayment()).isGreaterThan(new BigDecimal("10000"));
        assertThat(resp.getTotalInterest()).isGreaterThan(BigDecimal.ZERO);
        // totalRepayment = totalInterest + amount
        assertThat(resp.getTotalRepayment())
                .isEqualByComparingTo(resp.getAmount().add(resp.getTotalInterest()));
    }

    @Test
    void simulate_shortLoan_shouldReturnHigherMonthlyPayment() {
        LoanSimulationResponse resp12 = loanService.simulate(new BigDecimal("10000"), 12);
        LoanSimulationResponse resp36 = loanService.simulate(new BigDecimal("10000"), 36);

        assertThat(resp12.getMonthlyPayment()).isGreaterThan(resp36.getMonthlyPayment());
        assertThat(resp12.getTotalInterest()).isLessThan(resp36.getTotalInterest());
    }

    // ── apply ──────────────────────────────────────────────────────────────────

    @Test
    void apply_shouldSaveLoanWithPendingStatus() {
        LoanApplicationRequest req = new LoanApplicationRequest();
        req.setAmount(new BigDecimal("5000"));
        req.setDurationMonths(24);
        req.setAccountId(ACCOUNT_ID);
        req.setPurpose("Home renovation");

        Loan savedLoan = Loan.builder()
                .id(LOAN_ID).userId(USER_ID).accountId(ACCOUNT_ID)
                .amount(req.getAmount()).durationMonths(24)
                .status(Loan.LoanStatus.PENDING)
                .interestRate(new BigDecimal("5.50"))
                .monthlyPayment(new BigDecimal("220.00"))
                .totalRepayment(new BigDecimal("5280.00"))
                .purpose("Home renovation")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        when(loanRepository.save(any(Loan.class))).thenReturn(savedLoan);

        Loan result = loanService.apply(USER_ID, req);

        ArgumentCaptor<Loan> captor = ArgumentCaptor.forClass(Loan.class);
        verify(loanRepository).save(captor.capture());
        Loan captured = captor.getValue();

        assertThat(captured.getUserId()).isEqualTo(USER_ID);
        assertThat(captured.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(captured.getStatus()).isEqualTo(Loan.LoanStatus.PENDING);
        assertThat(captured.getAmount()).isEqualByComparingTo("5000");
        assertThat(captured.getMonthlyPayment()).isNotNull();
        assertThat(captured.getPurpose()).isEqualTo("Home renovation");
        assertThat(result).isEqualTo(savedLoan);
    }

    // ── getUserLoans ───────────────────────────────────────────────────────────

    @Test
    void getUserLoans_shouldReturnListFromRepository() {
        Loan loan = Loan.builder().id(LOAN_ID).userId(USER_ID)
                .status(Loan.LoanStatus.PENDING).build();
        when(loanRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(loan));

        List<Loan> result = loanService.getUserLoans(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(LOAN_ID);
    }

    // ── getPendingLoans ────────────────────────────────────────────────────────

    @Test
    void getPendingLoans_shouldReturnPendingLoans() {
        Loan loan = Loan.builder().id(LOAN_ID).status(Loan.LoanStatus.PENDING).build();
        when(loanRepository.findByStatusOrderByCreatedAtDesc(Loan.LoanStatus.PENDING))
                .thenReturn(List.of(loan));

        List<Loan> result = loanService.getPendingLoans();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(Loan.LoanStatus.PENDING);
    }

    // ── approveOrReject ────────────────────────────────────────────────────────

    @Test
    void approveOrReject_approve_shouldSetApprovedAndDisbursedAt() {
        Loan loan = Loan.builder().id(LOAN_ID).status(Loan.LoanStatus.PENDING)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(loanRepository.findById(LOAN_ID)).thenReturn(Optional.of(loan));
        when(loanRepository.save(loan)).thenReturn(loan);

        Loan result = loanService.approveOrReject(LOAN_ID, true, "Looks good");

        assertThat(loan.getStatus()).isEqualTo(Loan.LoanStatus.APPROVED);
        assertThat(loan.getAdminNote()).isEqualTo("Looks good");
        assertThat(loan.getDisbursedAt()).isNotNull();
    }

    @Test
    void approveOrReject_reject_shouldSetRejectedAndNoDisbursedAt() {
        Loan loan = Loan.builder().id(LOAN_ID).status(Loan.LoanStatus.PENDING)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(loanRepository.findById(LOAN_ID)).thenReturn(Optional.of(loan));
        when(loanRepository.save(loan)).thenReturn(loan);

        loanService.approveOrReject(LOAN_ID, false, "Risk too high");

        assertThat(loan.getStatus()).isEqualTo(Loan.LoanStatus.REJECTED);
        assertThat(loan.getAdminNote()).isEqualTo("Risk too high");
        assertThat(loan.getDisbursedAt()).isNull();
    }

    @Test
    void approveOrReject_loanNotFound_shouldThrow() {
        when(loanRepository.findById(LOAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.approveOrReject(LOAN_ID, true, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Loan not found");
    }
}
