package com.solarisbank.transaction_service.controller;

import com.solarisbank.transaction_service.dto.TransactionResponse;
import com.solarisbank.transaction_service.dto.TransferRequest;
import com.solarisbank.transaction_service.service.StatementService;
import com.solarisbank.transaction_service.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final StatementService   statementService;

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

    /**
     * Generates and returns a PDF bank statement for the given account.
     * Ownership is validated inside StatementService via accountClient.getAccount().
     */
    @GetMapping("/statement")
    public ResponseEntity<byte[]> getStatement(
            @RequestParam UUID accountId,
            @RequestHeader("X-User-Id") UUID userId) {

        byte[] pdf = statementService.generateStatement(accountId, userId);

        String filename = "releve-" + LocalDate.now() + ".pdf";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(filename).build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdf);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getTransaction(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId) {

        return ResponseEntity.ok(transactionService.getTransaction(id, userId));
    }
}
