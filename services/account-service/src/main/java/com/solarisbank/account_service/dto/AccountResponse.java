package com.solarisbank.account_service.dto;

import com.solarisbank.account_service.model.Account;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class AccountResponse {
    private UUID id;
    private String iban;
    private Account.Type type;
    private BigDecimal balance;
    private String currency;
    private Account.Status status;
    private LocalDateTime createdAt;
}
