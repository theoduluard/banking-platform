package com.solarisbank.messaging_service.dto;

import com.solarisbank.messaging_service.model.Message;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class MessageResponse {
    private UUID id;
    private UUID userId;
    private String subject;
    private String body;
    private Message.Type type;
    private boolean isRead;
    private String attachmentBase64;
    private String attachmentContentType;
    private String attachmentFilename;
    private LocalDateTime createdAt;
}
