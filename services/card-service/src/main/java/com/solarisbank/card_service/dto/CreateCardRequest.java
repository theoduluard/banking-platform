package com.solarisbank.card_service.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

@Getter @Setter
public class CreateCardRequest {
    @NotNull private UUID accountId;
    private String cardholderName;
    private String cardType; // VIRTUAL or PHYSICAL
    private BigDecimal spendingLimit;
}
