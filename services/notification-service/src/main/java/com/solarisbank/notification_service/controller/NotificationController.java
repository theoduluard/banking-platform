package com.solarisbank.notification_service.controller;

import com.solarisbank.notification_service.dto.NotificationResponse;
import com.solarisbank.notification_service.exception.BusinessException;
import com.solarisbank.notification_service.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Returns a paginated list of the authenticated user's notifications,
     * most recent first.
     *
     * @param page zero-based page number (default 0)
     * @param size page size (default 20, max 100)
     */
    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getNotifications(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID userId = extractUserId(request);
        int safeSize = Math.min(size, 100);
        Page<NotificationResponse> result = notificationService
                .getNotifications(userId, page, safeSize)
                .map(NotificationResponse::from);
        return ResponseEntity.ok(result);
    }

    /**
     * Returns the count of unread notifications — used by the frontend bell badge.
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(HttpServletRequest request) {
        UUID userId = extractUserId(request);
        long count = notificationService.countUnread(userId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * Marks a single notification as read.
     * 404 if the notification does not exist or belongs to another user.
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markRead(
            HttpServletRequest request,
            @PathVariable UUID id) {

        UUID userId = extractUserId(request);
        return ResponseEntity.ok(
                NotificationResponse.from(notificationService.markRead(id, userId)));
    }

    /**
     * Marks all of the authenticated user's notifications as read.
     * Returns the number of notifications updated.
     */
    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllRead(HttpServletRequest request) {
        UUID userId = extractUserId(request);
        int updated = notificationService.markAllRead(userId);
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Extracts the authenticated user's UUID from the X-User-Id header injected
     * by the api-gateway after JWT validation.
     */
    private UUID extractUserId(HttpServletRequest request) {
        String header = request.getHeader("X-User-Id");
        if (header == null || header.isBlank()) {
            throw new BusinessException("Missing X-User-Id header",
                    org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
        try {
            return UUID.fromString(header);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid X-User-Id header",
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }
    }
}
