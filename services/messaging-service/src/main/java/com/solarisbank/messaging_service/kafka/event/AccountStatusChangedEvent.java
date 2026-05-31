package com.solarisbank.messaging_service.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountStatusChangedEvent {
    private UUID accountId;
    private UUID userId;
    /** Human-readable IBAN of the account */
    private String iban;
    /** 'CHECKING' or 'SAVINGS' */
    private String accountType;
}
