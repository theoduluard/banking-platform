package com.solarisbank.account_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BeneficiaryRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @NotBlank(message = "IBAN is required")
    @Pattern(
        regexp = "^[A-Z]{2}[0-9]{2}[A-Z0-9]{4,30}$",
        message = "Invalid IBAN format"
    )
    private String iban;
}
