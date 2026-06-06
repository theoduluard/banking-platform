package com.solarisbank.auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ConfirmEmailChangeOtpRequest {

    @NotBlank
    @Pattern(regexp = "\\d{6}", message = "Code must be exactly 6 digits")
    private String code;
}
