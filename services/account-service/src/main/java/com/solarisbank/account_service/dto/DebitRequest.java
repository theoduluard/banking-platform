package com.solarisbank.account_service.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class DebitRequest {
    private UUID userId;
    private BigDecimal amount;
}
