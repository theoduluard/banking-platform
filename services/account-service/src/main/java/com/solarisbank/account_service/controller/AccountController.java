package com.solarisbank.account_service.controller;

import com.solarisbank.account_service.dto.AccountResponse;
import com.solarisbank.account_service.dto.CreateAccountRequest;
import com.solarisbank.account_service.model.Account;
import com.solarisbank.account_service.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
}