package com.solarisbank.transaction_service.client.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class AccountResponse {
    private UUID id;
    private String iban;
    private BigDecimal balance;
    private String status;
    private UUID userId;
}
