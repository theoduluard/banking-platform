package com.solarisbank.messaging_service.dto;

import com.solarisbank.messaging_service.model.SupportRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateRequestRequest {

    @NotNull(message = "Type requis")
    private SupportRequest.Type type;

    @NotBlank(message = "Objet requis")
    private String subject;

    @NotBlank(message = "Message requis")
    private String body;

    private String attachmentBase64;
    private String attachmentContentType;
    private String attachmentFilename;
}
