package com.solarisbank.messaging_service.dto;

import com.solarisbank.messaging_service.model.SupportRequest;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class SupportRequestResponse {
    private UUID id;
    private UUID userId;
    private SupportRequest.Type type;
    private String subject;
    private String body;
    private SupportRequest.Status status;
    private boolean hasAttachment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
