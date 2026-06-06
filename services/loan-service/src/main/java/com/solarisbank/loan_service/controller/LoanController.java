package com.solarisbank.loan_service.controller;

import com.solarisbank.loan_service.dto.LoanApplicationRequest;
import com.solarisbank.loan_service.dto.LoanSimulationRequest;
import com.solarisbank.loan_service.dto.LoanSimulationResponse;
import com.solarisbank.loan_service.model.Loan;
import com.solarisbank.loan_service.service.LoanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService loanService;

    @PostMapping("/simulate")
    public ResponseEntity<LoanSimulationResponse> simulate(@Valid @RequestBody LoanSimulationRequest req) {
        return ResponseEntity.ok(loanService.simulate(req.getAmount(), req.getDurationMonths()));
    }

    @PostMapping
    public ResponseEntity<Loan> apply(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody LoanApplicationRequest req) {
        return ResponseEntity.ok(loanService.apply(userId, req));
    }

    @GetMapping
    public ResponseEntity<List<Loan>> getMyLoans(@RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(loanService.getUserLoans(userId));
    }
}
