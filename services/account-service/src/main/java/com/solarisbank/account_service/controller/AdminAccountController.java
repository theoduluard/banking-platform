package com.solarisbank.account_service.controller;

import com.solarisbank.account_service.dto.AccountResponse;
import com.solarisbank.account_service.dto.CreditRequest;
import com.solarisbank.account_service.dto.VerificationDocumentResponse;
import com.solarisbank.account_service.exception.BusinessException;
import com.solarisbank.account_service.model.Account;
import com.solarisbank.account_service.repository.AccountRepository;
import com.solarisbank.account_service.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin endpoints for account management.
 * Access is gated at the API Gateway (X-User-Role=ADMIN).
 * The X-User-Role header is validated here as defence-in-depth.
 */
@RestController
@RequestMapping("/api/v1/admin/accounts")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AccountRepository accountRepository;
    private final AccountService accountService;

    // ── All accounts (paginated) ───────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<Page<AccountResponse>> getAllAccounts(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("X-User-Role") String userRole) {

        requireAdmin(userRole);

        Page<AccountResponse> accounts = accountRepository
                .findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(this::toResponse);

        return ResponseEntity.ok(accounts);
    }

    // ── Pending approvals ─────────────────────────────────────────────────────

    @GetMapping("/pending")
    public ResponseEntity<List<AccountResponse>> getPendingAccounts(
            @RequestHeader("X-User-Role") String userRole) {

        requireAdmin(userRole);
        return ResponseEntity.ok(accountService.getPendingAccounts());
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<AccountResponse> approveAccount(
            @PathVariable UUID id,
            @RequestHeader("X-User-Role") String userRole) {

        requireAdmin(userRole);
        return ResponseEntity.ok(accountService.approveAccount(id));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<AccountResponse> rejectAccount(
            @PathVariable UUID id,
            @RequestHeader("X-User-Role") String userRole) {

        requireAdmin(userRole);
        return ResponseEntity.ok(accountService.rejectAccount(id));
    }

    // ── KYC documents ─────────────────────────────────────────────────────────

    @GetMapping("/{id}/documents")
    public ResponseEntity<VerificationDocumentResponse> getDocuments(
            @PathVariable UUID id,
            @RequestHeader("X-User-Role") String userRole) {

        requireAdmin(userRole);
        return ResponseEntity.ok(accountService.getAccountDocuments(id));
    }

    // ── Status patch ──────────────────────────────────────────────────────────

    @PatchMapping("/{id}/status")
    public ResponseEntity<AccountResponse> updateAccountStatus(
            @PathVariable UUID id,
            @RequestParam Account.Status status,
            @RequestHeader("X-User-Role") String userRole) {

        requireAdmin(userRole);

        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Account not found", HttpStatus.NOT_FOUND));

        account.setStatus(status);
        accountRepository.save(account);

        return ResponseEntity.ok(toResponse(account));
    }

    // ── Balance operations ────────────────────────────────────────────────────

    @PostMapping("/{id}/deposit")
    public ResponseEntity<AccountResponse> adminDeposit(
            @PathVariable UUID id,
            @Valid @RequestBody CreditRequest request,
            @RequestHeader("X-User-Role") String userRole) {

        requireAdmin(userRole);
        return ResponseEntity.ok(accountService.adminDeposit(id, request.getAmount()));
    }

    @PostMapping("/{id}/withdrawal")
    public ResponseEntity<AccountResponse> adminWithdrawal(
            @PathVariable UUID id,
            @Valid @RequestBody CreditRequest request,
            @RequestHeader("X-User-Role") String userRole) {

        requireAdmin(userRole);
        return ResponseEntity.ok(accountService.adminWithdrawal(id, request.getAmount()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void requireAdmin(String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new BusinessException("Forbidden", HttpStatus.FORBIDDEN);
        }
    }

    private AccountResponse toResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getAccountId())
                .userId(account.getUserId())
                .iban(account.getIban())
                .type(account.getType())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .status(account.getStatus())
                .createdAt(account.getCreatedAt())
                .build();
    }
}
