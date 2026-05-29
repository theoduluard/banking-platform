package com.solarisbank.account_service.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class BeneficiaryResponse {
    private UUID   id;
    private String name;
    private String iban;
    private LocalDateTime createdAt;
}
