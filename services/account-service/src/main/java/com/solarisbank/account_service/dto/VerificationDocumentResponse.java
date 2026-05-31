package com.solarisbank.account_service.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class VerificationDocumentResponse {
    private UUID id;
    private UUID accountId;
    private UUID userId;
    private String selfieBase64;
    private String selfieContentType;
    private String idCardBase64;
    private String idCardContentType;
    private LocalDateTime submittedAt;
}
