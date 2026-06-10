package com.solarisbank.card_service.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solarisbank.card_service.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Component
public class AccountClient {

    private final RestClient restClient;
    // ObjectMapper created directly — Spring Boot 4 uses Jackson 3.x as its primary mapper,
    // so com.fasterxml.jackson.databind.ObjectMapper is not registered as a bean.
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AccountClient(
            @Value("${account.service.url}") String accountServiceUrl,
            @Value("${internal.secret}") String internalSecret) {

        this.restClient = RestClient.builder()
                .baseUrl(accountServiceUrl)
                .defaultHeader("X-Internal-Secret", internalSecret)
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

    /**
     * Fetches an account by id, passing the userId for ownership validation.
     * Returns 404 (wrapped as BusinessException) if the account doesn't belong to the user.
     */
    public AccountResponse getAccount(UUID accountId, UUID userId) {
        return restClient.get()
                .uri("/api/v1/accounts/{id}", accountId)
                .header("X-User-Id", userId.toString())
                .retrieve()
                .body(AccountResponse.class);
    }
}
