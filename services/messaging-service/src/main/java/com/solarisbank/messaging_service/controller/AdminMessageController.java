package com.solarisbank.messaging_service.controller;

import com.solarisbank.messaging_service.dto.MessageResponse;
import com.solarisbank.messaging_service.dto.SendMessageRequest;
import com.solarisbank.messaging_service.exception.BusinessException;
import com.solarisbank.messaging_service.service.MessagingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/messages")
@RequiredArgsConstructor
public class AdminMessageController {

    private final MessagingService messagingService;

    @PostMapping
    public ResponseEntity<MessageResponse> sendMessage(
            @Valid @RequestBody SendMessageRequest request,
            @RequestHeader("X-User-Role") String userRole) {

        requireAdmin(userRole);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(messagingService.sendMessage(request));
    }

    @GetMapping
    public ResponseEntity<Page<MessageResponse>> getAllMessages(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("X-User-Role") String userRole) {

        requireAdmin(userRole);
        return ResponseEntity.ok(messagingService.getAllMessages(page, size));
    }

    private void requireAdmin(String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new BusinessException("Forbidden", HttpStatus.FORBIDDEN);
        }
    }
}
