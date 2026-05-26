package com.solarisbank.account_service.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DebitRequestedEvent {
    private UUID transactionId;
    private UUID accountId;
    private UUID userId;
    private BigDecimal amount;
}
