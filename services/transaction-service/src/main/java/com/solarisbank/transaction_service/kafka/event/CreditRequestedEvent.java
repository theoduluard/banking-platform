package com.solarisbank.transaction_service.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreditRequestedEvent {
    private UUID transactionId;
    private UUID toAccountId;
    private UUID fromAccountId;  // conservé pour compensation si le crédit échoue
    private BigDecimal amount;
}
