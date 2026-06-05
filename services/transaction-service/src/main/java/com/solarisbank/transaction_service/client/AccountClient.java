package com.solarisbank.transaction_service.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solarisbank.transaction_service.client.dto.AccountResponse;
import com.solarisbank.transaction_service.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Component
public class AccountClient {

    private final RestClient restClient;

    public AccountClient(
            @Value("${account.service.url}") String accountServiceUrl,
            @Value("${internal.secret}") String internalSecret,
            ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl(accountServiceUrl)
                // Authenticate all service-to-service calls with the shared internal secret.
                // account-service's InternalRequestFilter validates this header.
                .defaultHeader("X-Internal-Secret", internalSecret)
                // Convert 4xx/5xx responses from account-service into BusinessException
                // so they propagate cleanly through the transaction-service error handlers.
                .defaultStatusHandler(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    (req, res) -> {
                        String msg = "Account service error";
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> body = objectMapper.readValue(
                                    res.getBody().readAllBytes(), Map.class);
                            if (body.containsKey("error")) msg = (String) body.get("error");
                        } catch (Exception ignored) {}
                        throw new BusinessException(msg, HttpStatus.valueOf(res.getStatusCode().value()));
                    }
                )
                .build();
    }

    public AccountResponse getAccount(UUID accountId, UUID userId) {
        return restClient.get()
                .uri("/api/v1/accounts/{id}", accountId)
                .header("X-User-Id", userId.toString())
                .retrieve()
                .body(AccountResponse.class);
    }

    /**
     * Fetches account metadata using the internal secret (no user ownership check).
     * Used by the notification event publisher to resolve the recipient's userId from
     * their accountId at saga completion — without requiring the recipient's session token.
     */
    public AccountResponse getAccountInternal(UUID accountId) {
        return restClient.get()
                .uri("/api/v1/accounts/{id}/internal", accountId)
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

    // ── Admin operations (bypass user ownership check) ────────────────────────

    public void adminDeposit(UUID accountId, BigDecimal amount) {
        restClient.post()
                .uri("/api/v1/admin/accounts/{id}/deposit", accountId)
                .header("X-User-Role", "ADMIN")
                .body(Map.of("amount", amount))
                .retrieve()
                .toBodilessEntity();
    }

    public void adminWithdrawal(UUID accountId, BigDecimal amount) {
        restClient.post()
                .uri("/api/v1/admin/accounts/{id}/withdrawal", accountId)
                .header("X-User-Role", "ADMIN")
                .body(Map.of("amount", amount))
                .retrieve()
                .toBodilessEntity();
    }
}
