package com.solarisbank.messaging_service.controller;

import com.solarisbank.messaging_service.dto.AddReplyRequest;
import com.solarisbank.messaging_service.dto.CreateRequestRequest;
import com.solarisbank.messaging_service.dto.SupportRequestDetailResponse;
import com.solarisbank.messaging_service.dto.SupportRequestResponse;
import com.solarisbank.messaging_service.service.MessagingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/requests")
@RequiredArgsConstructor
public class RequestController {

    private final MessagingService messagingService;

    @PostMapping
    public ResponseEntity<SupportRequestResponse> createRequest(
            @Valid @RequestBody CreateRequestRequest request,
            @RequestHeader("X-User-Id") UUID userId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(messagingService.createRequest(userId, request));
    }

    @GetMapping
    public ResponseEntity<List<SupportRequestResponse>> getMyRequests(
            @RequestHeader("X-User-Id") UUID userId) {

        return ResponseEntity.ok(messagingService.getMyRequests(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SupportRequestDetailResponse> getRequestDetail(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId) {

        return ResponseEntity.ok(messagingService.getRequestDetail(id, userId));
    }

    @PostMapping("/{id}/replies")
    public ResponseEntity<SupportRequestDetailResponse> addReply(
            @PathVariable UUID id,
            @Valid @RequestBody AddReplyRequest request,
            @RequestHeader("X-User-Id") UUID userId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(messagingService.addClientReply(id, userId, request));
    }
}
