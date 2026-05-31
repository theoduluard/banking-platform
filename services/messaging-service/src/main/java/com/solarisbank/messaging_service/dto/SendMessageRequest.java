package com.solarisbank.messaging_service.dto;

import com.solarisbank.messaging_service.model.Message;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class SendMessageRequest {

    @NotNull(message = "Destinataire requis")
    private UUID userId;

    @NotBlank(message = "Objet requis")
    private String subject;

    @NotBlank(message = "Corps requis")
    private String body;

    private Message.Type type = Message.Type.INFO;

    private String attachmentBase64;
    private String attachmentContentType;
    private String attachmentFilename;
}
