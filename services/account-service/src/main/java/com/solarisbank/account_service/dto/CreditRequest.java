package com.solarisbank.account_service.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreditRequest {
    private BigDecimal amount;
}
