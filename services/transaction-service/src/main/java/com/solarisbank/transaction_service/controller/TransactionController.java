package com.solarisbank.transaction_service.controller;

import com.solarisbank.transaction_service.dto.TransactionResponse;
import com.solarisbank.transaction_service.dto.TransferRequest;
import com.solarisbank.transaction_service.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader("X-User-Id") UUID userId,
            // Optional: not every client sends this header (graceful degradation)
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        // 202 Accepted : la saga est lancée, le statut sera mis à jour de façon asynchrone
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(transactionService.transfer(userId, request, idempotencyKey));
    }

    @GetMapping
    public ResponseEntity<Page<TransactionResponse>> getHistory(
            @RequestParam UUID accountId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(transactionService.getHistory(accountId, userId, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getTransaction(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId) {

        return ResponseEntity.ok(transactionService.getTransaction(id, userId));
    }
}
