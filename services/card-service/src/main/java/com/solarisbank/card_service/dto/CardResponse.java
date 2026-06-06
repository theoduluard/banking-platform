package com.solarisbank.card_service.dto;

import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Builder
public class CardResponse {
    private UUID id;
    private UUID accountId;
    private String maskedNumber;
    private String cardholderName;
    private String cardType;
    private String status;
    private short expiryMonth;
    private short expiryYear;
    private BigDecimal spendingLimit;
    private LocalDateTime createdAt;
}
