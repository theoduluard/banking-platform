package com.solarisbank.account_service.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountApprovedEvent {
    private UUID accountId;
    private UUID userId;
    private String iban;
    private String accountType;
}
