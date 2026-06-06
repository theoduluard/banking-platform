package com.solarisbank.fraud_service.controller;

import com.solarisbank.fraud_service.model.FraudAlert;
import com.solarisbank.fraud_service.repository.FraudAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class FraudController {

    private final FraudAlertRepository repo;

    @GetMapping("/api/v1/fraud/alerts")
    public ResponseEntity<List<FraudAlert>> getMyAlerts(@RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(repo.findByUserIdOrderByCreatedAtDesc(userId));
    }

    // Admin endpoints
    @GetMapping("/api/v1/admin/fraud/alerts")
    public ResponseEntity<List<FraudAlert>> getAllOpen() {
        return ResponseEntity.ok(repo.findByStatusOrderByCreatedAtDesc(FraudAlert.AlertStatus.OPEN));
    }

    @PostMapping("/api/v1/admin/fraud/alerts/{id}/resolve")
    public ResponseEntity<FraudAlert> resolve(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID adminId,
            @RequestBody Map<String, String> body) {
        FraudAlert alert = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found"));
        FraudAlert.AlertStatus newStatus = "FALSE_POSITIVE".equalsIgnoreCase(body.get("resolution"))
                ? FraudAlert.AlertStatus.FALSE_POSITIVE
                : FraudAlert.AlertStatus.RESOLVED;
        alert.setStatus(newStatus);
        alert.setResolvedBy(adminId);
        alert.setResolutionNote(body.get("note"));
        alert.setResolvedAt(LocalDateTime.now());
        return ResponseEntity.ok(repo.save(alert));
    }
}
