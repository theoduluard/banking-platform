package com.solarisbank.card_service.client;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Minimal projection of the account-service AccountResponse.
 * Only the fields card-service needs for validation are mapped.
 */
@Data
@NoArgsConstructor
public class AccountResponse {
    private UUID id;
    private UUID userId;
    private String iban;
    private String type;   // "CHECKING" or "SAVINGS"
    private String status; // "ACTIVE", "BLOCKED", etc.
}
