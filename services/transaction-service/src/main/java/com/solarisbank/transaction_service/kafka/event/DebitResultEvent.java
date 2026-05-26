package com.solarisbank.transaction_service.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebitResultEvent {
    private UUID transactionId;
    private boolean success;
    private String errorMessage;
}
