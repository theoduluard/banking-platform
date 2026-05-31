package com.solarisbank.account_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerificationDocumentRequest {

    @NotBlank(message = "Selfie requis")
    private String selfieBase64;

    @NotBlank(message = "Type du selfie requis")
    private String selfieContentType;

    @NotBlank(message = "Carte d'identité requise")
    private String idCardBase64;

    @NotBlank(message = "Type de la carte d'identité requis")
    private String idCardContentType;
}
