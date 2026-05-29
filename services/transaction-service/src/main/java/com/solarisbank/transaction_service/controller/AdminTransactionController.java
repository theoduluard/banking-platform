package com.solarisbank.transaction_service.controller;

import com.solarisbank.transaction_service.dto.AdminOperationRequest;
import com.solarisbank.transaction_service.dto.TransactionResponse;
import com.solarisbank.transaction_service.exception.BusinessException;
import com.solarisbank.transaction_service.model.Transaction;
import com.solarisbank.transaction_service.repository.TransactionRepository;
import com.solarisbank.transaction_service.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin endpoints for transaction oversight and manual balance adjustments.
 * Access is gated at the API Gateway (X-User-Role=ADMIN).
 * The X-User-Role header is validated here as defence-in-depth.
 */
@RestController
@RequestMapping("/api/v1/admin/transactions")
@RequiredArgsConstructor
public class AdminTransactionController {

    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;

    @GetMapping
    public ResponseEntity<Page<TransactionResponse>> getAllTransactions(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("X-User-Role") String userRole) {

        requireAdmin(userRole);

        Page<TransactionResponse> transactions = transactionRepository
                .findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(this::toResponse);

        return ResponseEntity.ok(transactions);
    }

    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponse> adminDeposit(
            @Valid @RequestBody AdminOperationRequest request,
            @RequestHeader("X-User-Id") UUID adminId,
            @RequestHeader("X-User-Role") String userRole) {

        requireAdmin(userRole);
        return ResponseEntity.ok(transactionService.adminDeposit(adminId, request));
    }

    @PostMapping("/withdrawal")
    public ResponseEntity<TransactionResponse> adminWithdrawal(
            @Valid @RequestBody AdminOperationRequest request,
            @RequestHeader("X-User-Id") UUID adminId,
            @RequestHeader("X-User-Role") String userRole) {

        requireAdmin(userRole);
        return ResponseEntity.ok(transactionService.adminWithdrawal(adminId, request));
    }

    private void requireAdmin(String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new BusinessException("Forbidden", HttpStatus.FORBIDDEN);
        }
    }

    private TransactionResponse toResponse(Transaction tx) {
        return TransactionResponse.builder()
                .id(tx.getId())
                .fromAccountId(tx.getFromAccountId())
                .toAccountId(tx.getToAccountId())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .type(tx.getType())
                .status(tx.getStatus())
                .description(tx.getDescription())
                .createdAt(tx.getCreatedAt())
                .completedAt(tx.getCompletedAt())
                .build();
    }
}
