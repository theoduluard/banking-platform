package com.solarisbank.messaging_service.dto;

import com.solarisbank.messaging_service.model.SupportRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminReplyRequest {

    @NotBlank(message = "Message requis")
    private String body;

    /** Optional: change the request status when replying */
    private SupportRequest.Status newStatus;

    private String attachmentBase64;
    private String attachmentContentType;
    private String attachmentFilename;
}
