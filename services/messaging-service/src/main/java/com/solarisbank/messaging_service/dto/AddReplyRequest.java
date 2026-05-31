package com.solarisbank.messaging_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddReplyRequest {

    @NotBlank(message = "Message requis")
    private String body;

    private String attachmentBase64;
    private String attachmentContentType;
    private String attachmentFilename;
}
