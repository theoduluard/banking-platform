package com.solarisbank.audit_service.controller;

import com.solarisbank.audit_service.model.AuditEvent;
import com.solarisbank.audit_service.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AuditController {

    private final AuditEventRepository repo;

    @GetMapping("/api/v1/audit/my-events")
    public ResponseEntity<Page<AuditEvent>> getMyEvents(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return ResponseEntity.ok(repo.findByUserIdOrderByCreatedAtDesc(userId, pageable));
    }

    // Admin
    @GetMapping("/api/v1/admin/audit/events")
    public ResponseEntity<Page<AuditEvent>> getAllEvents(
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200));
        if (eventType != null) {
            return ResponseEntity.ok(repo.findByEventTypeOrderByCreatedAtDesc(eventType, pageable));
        }
        return ResponseEntity.ok(repo.findAllByOrderByCreatedAtDesc(pageable));
    }
}
