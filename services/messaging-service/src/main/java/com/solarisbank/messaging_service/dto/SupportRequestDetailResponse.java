package com.solarisbank.messaging_service.dto;

import com.solarisbank.messaging_service.model.SupportRequest;
import com.solarisbank.messaging_service.model.SupportRequestReply;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class SupportRequestDetailResponse {
    private UUID id;
    private UUID userId;
    private SupportRequest.Type type;
    private String subject;
    private String body;
    private SupportRequest.Status status;
    private String attachmentBase64;
    private String attachmentContentType;
    private String attachmentFilename;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ReplyResponse> replies;

    @Data
    @Builder
    public static class ReplyResponse {
        private UUID id;
        private SupportRequestReply.AuthorType authorType;
        private UUID authorId;
        private String body;
        private String attachmentBase64;
        private String attachmentContentType;
        private String attachmentFilename;
        private LocalDateTime createdAt;
    }
}
