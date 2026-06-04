package com.solarisbank.account_service.controller;

import com.solarisbank.account_service.dto.AccountResponse;
import com.solarisbank.account_service.dto.CreateAccountRequest;
import com.solarisbank.account_service.dto.VerificationDocumentRequest;
import com.solarisbank.account_service.model.Account;
import java.util.HashMap;
import com.solarisbank.account_service.exception.BusinessException;
import com.solarisbank.account_service.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

    // Fix 11: used to restrict the /credit endpoint to internal service-to-service calls only.
    @Value("${internal.secret}")
    private String internalSecret;

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
     * Fix 18: require X-User-Id so only authenticated users can call this endpoint.
     * Without it, any unauthenticated caller could enumerate accounts by probing
     * predictable IBANs. The header is already injected by the api-gateway JWT filter.
     */
    @GetMapping("/iban/{iban}")
    public ResponseEntity<Map<String, String>> getByIban(
            @PathVariable String iban,
            @RequestHeader("X-User-Id") UUID userId) {
        return accountService.findByIban(iban)
                .map(acc -> ResponseEntity.ok(Map.of("accountId", acc.getAccountId().toString())))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Submits KYC documents (selfie + ID card) for the authenticated user.
     * Called right after first login — no account needed yet.
     */
    @PostMapping("/kyc")
    public ResponseEntity<Void> submitKyc(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody VerificationDocumentRequest request) {

        accountService.submitKyc(userId, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns whether the authenticated user has already submitted their KYC documents.
     */
    @GetMapping("/kyc/status")
    public ResponseEntity<Map<String, Boolean>> getKycStatus(
            @RequestHeader("X-User-Id") UUID userId) {

        Map<String, Boolean> result = new HashMap<>();
        result.put("submitted", accountService.hasKyc(userId));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/debit")
    public ResponseEntity<Void> debit(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody Map<String, BigDecimal> body) {

        accountService.debit(id, userId, body.get("amount"));
        return ResponseEntity.noContent().build();
    }

    /**
     * Fix 11: /credit is an internal endpoint called only by transaction-service.
     * Unlike /debit (which is ownership-checked via X-User-Id + findByAccountIdAndUserId),
     * /credit performs no ownership validation — any authenticated user reaching it could
     * credit any account arbitrarily.
     * Mitigation: require the X-Internal-Secret header, which is only known to backend services.
     * A regular JWT user routed through the api-gateway carries a valid X-User-Id but NOT
     * the internal secret, so they cannot reach this endpoint.
     */
    @PostMapping("/{id}/credit")
    public ResponseEntity<Void> credit(
            @PathVariable UUID id,
            @RequestBody Map<String, BigDecimal> body,
            @RequestHeader(value = "X-Internal-Secret", required = false) String providedSecret) {

        if (!internalSecret.equals(providedSecret)) {
            throw new BusinessException("Unauthorized — internal endpoint", HttpStatus.UNAUTHORIZED);
        }
        accountService.credit(id, body.get("amount"));
        return ResponseEntity.noContent().build();
    }
}