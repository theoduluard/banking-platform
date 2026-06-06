package com.solarisbank.loan_service.repository;

import com.solarisbank.loan_service.model.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface LoanRepository extends JpaRepository<Loan, UUID> {
    List<Loan> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<Loan> findByStatusOrderByCreatedAtDesc(Loan.LoanStatus status);
}
