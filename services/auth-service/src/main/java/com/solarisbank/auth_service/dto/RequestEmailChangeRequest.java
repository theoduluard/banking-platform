package com.solarisbank.auth_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RequestEmailChangeRequest {

    @NotBlank
    @Email(message = "Must be a valid email address")
    private String newEmail;

    @NotBlank
    private String currentPassword;
}
