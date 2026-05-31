package com.solarisbank.messaging_service.controller;

import com.solarisbank.messaging_service.dto.AdminReplyRequest;
import com.solarisbank.messaging_service.dto.SupportRequestDetailResponse;
import com.solarisbank.messaging_service.dto.SupportRequestResponse;
import com.solarisbank.messaging_service.exception.BusinessException;
import com.solarisbank.messaging_service.model.SupportRequest;
import com.solarisbank.messaging_service.service.MessagingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/requests")
@RequiredArgsConstructor
public class AdminRequestController {

    private final MessagingService messagingService;

    @GetMapping
    public ResponseEntity<Page<SupportRequestResponse>> getAllRequests(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) SupportRequest.Status status,
            @RequestHeader("X-User-Role") String userRole) {

        requireAdmin(userRole);
        return ResponseEntity.ok(messagingService.getAllRequests(page, size, status));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStats(
            @RequestHeader("X-User-Role") String userRole) {

        requireAdmin(userRole);
        return ResponseEntity.ok(Map.of("openCount", messagingService.countOpenRequests()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SupportRequestDetailResponse> getRequestDetail(
            @PathVariable UUID id,
            @RequestHeader("X-User-Role") String userRole) {

        requireAdmin(userRole);
        return ResponseEntity.ok(messagingService.getRequestDetailAdmin(id));
    }

    @PostMapping("/{id}/replies")
    public ResponseEntity<SupportRequestDetailResponse> adminReply(
            @PathVariable UUID id,
            @Valid @RequestBody AdminReplyRequest request,
            @RequestHeader("X-User-Id") UUID adminId,
            @RequestHeader("X-User-Role") String userRole) {

        requireAdmin(userRole);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(messagingService.adminReply(id, adminId, request));
    }

    private void requireAdmin(String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new BusinessException("Forbidden", HttpStatus.FORBIDDEN);
        }
    }
}
