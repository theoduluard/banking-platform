package com.solarisbank.account_service.controller;

import com.solarisbank.account_service.dto.AccountResponse;
import com.solarisbank.account_service.dto.CreateAccountRequest;
import com.solarisbank.account_service.dto.VerificationDocumentRequest;
import com.solarisbank.account_service.model.Account;
import com.solarisbank.account_service.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<AccountResponse> create(
            @Valid @RequestBody CreateAccountRequest request,
            @AuthenticationPrincipal String email,
            @RequestHeader("X-User-Id") UUID userId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.create(userId, request));
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getMyAccounts(
            @RequestHeader("X-User-Id") UUID userId) {

        return ResponseEntity.ok(accountService.getMyAccounts(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccount(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId) {

        return ResponseEntity.ok(accountService.getAccount(id, userId));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<AccountResponse> updateStatus(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam Account.Status status) {

        return ResponseEntity.ok(accountService.updateStatus(id, userId, status));
    }

    /**
     * Resolves an IBAN to an account ID.
     * Used by the frontend to look up the destination account before a transfer.
     * Only returns the account ID — no balance, no status (privacy).
     */
    @GetMapping("/iban/{iban}")
    public ResponseEntity<Map<String, String>> getByIban(@PathVariable String iban) {
        return accountService.findByIban(iban)
                .map(acc -> ResponseEntity.ok(Map.of("accountId", acc.getAccountId().toString())))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Submits KYC documents (selfie + ID card) for a pending account.
     * Required for first account creation before admin can approve.
     */
    @PostMapping("/{id}/documents")
    public ResponseEntity<Void> submitDocuments(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody VerificationDocumentRequest request) {

        accountService.submitDocuments(id, userId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/debit")
    public ResponseEntity<Void> debit(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody Map<String, BigDecimal> body) {

        accountService.debit(id, userId, body.get("amount"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/credit")
    public ResponseEntity<Void> credit(
            @PathVariable UUID id,
            @RequestBody Map<String, BigDecimal> body) {

        accountService.credit(id, body.get("amount"));
        return ResponseEntity.noContent().build();
    }
}