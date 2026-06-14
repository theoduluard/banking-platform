package com.solarisbank.loan_service.controller;

import com.solarisbank.loan_service.model.Loan;
import com.solarisbank.loan_service.service.LoanService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/loans")
@RequiredArgsConstructor
public class AdminLoanController {

    private final LoanService loanService;

    @GetMapping
    public ResponseEntity<Page<Loan>> getAll(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(loanService.getAllLoans(page, size));
    }

    @GetMapping("/pending")
    public ResponseEntity<List<Loan>> getPending() {
        return ResponseEntity.ok(loanService.getPendingLoans());
    }

    @PostMapping("/{id}/decision")
    public ResponseEntity<Loan> decide(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        boolean approve = "APPROVE".equalsIgnoreCase(body.get("action"));
        return ResponseEntity.ok(loanService.approveOrReject(id, approve, body.get("note")));
    }
}
