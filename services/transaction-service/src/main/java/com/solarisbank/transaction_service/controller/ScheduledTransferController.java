package com.solarisbank.transaction_service.controller;

import com.solarisbank.transaction_service.dto.ScheduledTransferRequest;
import com.solarisbank.transaction_service.dto.ScheduledTransferResponse;
import com.solarisbank.transaction_service.service.ScheduledTransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scheduled-transfers")
@RequiredArgsConstructor
public class ScheduledTransferController {

    private final ScheduledTransferService scheduledTransferService;

    /** Create a new scheduled (recurring) transfer. */
    @PostMapping
    public ResponseEntity<ScheduledTransferResponse> create(
            @Valid @RequestBody ScheduledTransferRequest request,
            @RequestHeader("X-User-Id") UUID userId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(scheduledTransferService.create(userId, request));
    }

    /** List active scheduled transfers for the authenticated user. */
    @GetMapping
    public ResponseEntity<List<ScheduledTransferResponse>> getMyScheduledTransfers(
            @RequestHeader("X-User-Id") UUID userId) {

        return ResponseEntity.ok(scheduledTransferService.getMyScheduledTransfers(userId));
    }

    /** Cancel a scheduled transfer (sets active = false). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId) {

        scheduledTransferService.cancel(id, userId);
        return ResponseEntity.noContent().build();
    }
}
