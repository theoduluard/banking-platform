package com.solarisbank.loan_service.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "loans")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Loan {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "interest_rate", nullable = false)
    private BigDecimal interestRate;

    @Column(name = "duration_months", nullable = false)
    private int durationMonths;

    @Column(name = "monthly_payment", nullable = false)
    private BigDecimal monthlyPayment;

    @Column(name = "total_repayment", nullable = false)
    private BigDecimal totalRepayment;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private LoanStatus status;

    private String purpose;

    @Column(name = "admin_note")
    private String adminNote;

    @Column(name = "disbursed_at")
    private LocalDateTime disbursedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum LoanStatus { PENDING, APPROVED, REJECTED, DISBURSED, CLOSED }
}
