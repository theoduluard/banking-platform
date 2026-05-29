package com.solarisbank.account_service.controller;

import com.solarisbank.account_service.dto.BeneficiaryRequest;
import com.solarisbank.account_service.dto.BeneficiaryResponse;
import com.solarisbank.account_service.service.BeneficiaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/beneficiaries")
@RequiredArgsConstructor
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    @GetMapping
    public ResponseEntity<List<BeneficiaryResponse>> getAll(
            @RequestHeader("X-User-Id") UUID userId) {

        return ResponseEntity.ok(beneficiaryService.getAll(userId));
    }

    @PostMapping
    public ResponseEntity<BeneficiaryResponse> add(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody BeneficiaryRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(beneficiaryService.add(userId, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID id) {

        beneficiaryService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
