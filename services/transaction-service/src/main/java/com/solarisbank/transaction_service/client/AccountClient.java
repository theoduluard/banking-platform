package com.solarisbank.transaction_service.client;

import com.solarisbank.transaction_service.client.dto.AccountResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Component
public class AccountClient {

    private final RestClient restClient;

    public AccountClient(@Value("${account.service.url}") String accountServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(accountServiceUrl)
                .build();
    }

    public AccountResponse getAccount(UUID accountId, UUID userId) {
        return restClient.get()
                .uri("/api/v1/accounts/{id}", accountId)
                .header("X-User-Id", userId.toString())
                .retrieve()
                .body(AccountResponse.class);
    }

    public void debit(UUID accountId, UUID userId, BigDecimal amount) {
        restClient.post()
                .uri("/api/v1/accounts/{id}/debit", accountId)
                .header("X-User-Id", userId.toString())
                .body(Map.of("amount", amount))
                .retrieve()
                .toBodilessEntity();
    }

    public void credit(UUID accountId, BigDecimal amount) {
        restClient.post()
                .uri("/api/v1/accounts/{id}/credit", accountId)
                .body(Map.of("amount", amount))
                .retrieve()
                .toBodilessEntity();
    }
}
