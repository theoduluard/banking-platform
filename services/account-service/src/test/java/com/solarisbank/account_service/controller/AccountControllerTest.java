package com.solarisbank.account_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.solarisbank.account_service.dto.AccountResponse;
import com.solarisbank.account_service.exception.BusinessException;
import com.solarisbank.account_service.model.Account;
import com.solarisbank.account_service.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    @Value("${internal.secret}")
    private String internalSecret;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private UUID userId;
    private UUID accountId;
    private AccountResponse accountResponse;

    @BeforeEach
    void setUp() {
        userId    = UUID.randomUUID();
        accountId = UUID.randomUUID();

        accountResponse = AccountResponse.builder()
                .id(accountId)
                .iban("FR7630006000010000000000197")
                .type(Account.Type.CHECKING)
                .balance(BigDecimal.ZERO)
                .currency("EUR")
                .status(Account.Status.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ── POST /api/v1/accounts ──────────────────────────────────────────────────

    @Test
    void create_shouldReturn201_withAccountResponse() throws Exception {
        when(accountService.create(eq(userId), any())).thenReturn(accountResponse);

        mockMvc.perform(post("/api/v1/accounts")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CHECKING\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.iban").value("FR7630006000010000000000197"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void create_shouldReturn400_whenTypeIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/v1/accounts ───────────────────────────────────────────────────

    @Test
    void getMyAccounts_shouldReturn200_withAccountList() throws Exception {
        when(accountService.getMyAccounts(userId)).thenReturn(List.of(accountResponse));

        mockMvc.perform(get("/api/v1/accounts")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].iban").value("FR7630006000010000000000197"));
    }

    // ── GET /api/v1/accounts/{id} ──────────────────────────────────────────────

    @Test
    void getAccount_shouldReturn200_whenFound() throws Exception {
        when(accountService.getAccount(accountId, userId)).thenReturn(accountResponse);

        mockMvc.perform(get("/api/v1/accounts/{id}", accountId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accountId.toString()));
    }

    @Test
    void getAccount_shouldReturn404_whenNotFound() throws Exception {
        when(accountService.getAccount(accountId, userId))
                .thenThrow(new BusinessException("Account not found", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/v1/accounts/{id}", accountId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound());
    }

    // ── PUT /api/v1/accounts/{id}/status ──────────────────────────────────────

    @Test
    void updateStatus_shouldReturn200_withUpdatedAccount() throws Exception {
        AccountResponse blocked = AccountResponse.builder()
                .id(accountId).iban("FR7630006000010000000000197")
                .type(Account.Type.CHECKING).balance(BigDecimal.ZERO)
                .currency("EUR").status(Account.Status.BLOCKED).createdAt(LocalDateTime.now()).build();

        when(accountService.updateStatus(accountId, userId, Account.Status.BLOCKED)).thenReturn(blocked);

        mockMvc.perform(put("/api/v1/accounts/{id}/status", accountId)
                        .header("X-User-Id", userId.toString())
                        .param("status", "BLOCKED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));
    }

    // ── POST /api/v1/accounts/{id}/debit ──────────────────────────────────────

    @Test
    void debit_shouldReturn204_whenSuccessful() throws Exception {
        doNothing().when(accountService).debit(eq(accountId), eq(userId), any());

        mockMvc.perform(post("/api/v1/accounts/{id}/debit", accountId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", "100.00"))))
                .andExpect(status().isNoContent());
    }

    // ── POST /api/v1/accounts/{id}/credit ─────────────────────────────────────

    @Test
    void credit_shouldReturn204_whenSuccessful() throws Exception {
        doNothing().when(accountService).credit(eq(accountId), any());

        mockMvc.perform(post("/api/v1/accounts/{id}/credit", accountId)
                        .header("X-Internal-Secret", internalSecret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", "200.00"))))
                .andExpect(status().isNoContent());
    }

    // ── POST /api/v1/accounts/{id}/close ─────────────────────────────────────

    @Test
    void closeAccount_shouldReturn200_whenSuccessful() throws Exception {
        AccountResponse closed = AccountResponse.builder()
                .id(accountId).iban("FR7630006000010000000000197")
                .type(Account.Type.CHECKING).balance(BigDecimal.ZERO)
                .currency("EUR").status(Account.Status.CLOSED).createdAt(LocalDateTime.now()).build();

        when(accountService.closeAccount(accountId, userId)).thenReturn(closed);

        mockMvc.perform(post("/api/v1/accounts/{id}/close", accountId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void closeAccount_shouldReturn400_whenBalanceNotZero() throws Exception {
        when(accountService.closeAccount(accountId, userId))
                .thenThrow(new BusinessException("Account balance must be zero before closing", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/v1/accounts/{id}/close", accountId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Account balance must be zero before closing"));
    }

    @Test
    void closeAccount_shouldReturn400_whenAccountNotActive() throws Exception {
        when(accountService.closeAccount(accountId, userId))
                .thenThrow(new BusinessException("Only active accounts can be closed", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/v1/accounts/{id}/close", accountId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Only active accounts can be closed"));
    }

    // ── GET /api/v1/accounts/iban/{iban} ──────────────────────────────────────

    @Test
    void getByIban_shouldReturn200_withAccountId_whenFound() throws Exception {
        com.solarisbank.account_service.model.Account account =
                com.solarisbank.account_service.model.Account.builder()
                        .accountId(accountId)
                        .userId(userId)
                        .iban("FR7630006000010000000000197")
                        .type(com.solarisbank.account_service.model.Account.Type.CHECKING)
                        .balance(java.math.BigDecimal.ZERO)
                        .currency("EUR")
                        .status(com.solarisbank.account_service.model.Account.Status.ACTIVE)
                        .createdAt(LocalDateTime.now())
                        .build();

        when(accountService.findByIban("FR7630006000010000000000197"))
                .thenReturn(java.util.Optional.of(account));

        mockMvc.perform(get("/api/v1/accounts/iban/{iban}", "FR7630006000010000000000197")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()));
    }

    @Test
    void getByIban_shouldReturn404_whenNotFound() throws Exception {
        when(accountService.findByIban("FR0000000000000000000000000"))
                .thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/v1/accounts/iban/{iban}", "FR0000000000000000000000000")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/v1/accounts/kyc ──────────────────────────────────────────────

    @Test
    void submitKyc_shouldReturn204_whenSuccessful() throws Exception {
        doNothing().when(accountService).submitKyc(eq(userId), any());

        mockMvc.perform(post("/api/v1/accounts/kyc")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "selfieBase64", "data",
                                "selfieContentType", "image/jpeg",
                                "idCardBase64", "data",
                                "idCardContentType", "image/jpeg"))))
                .andExpect(status().isNoContent());

        verify(accountService).submitKyc(eq(userId), any());
    }

    @Test
    void submitKyc_shouldReturn400_whenFieldsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/kyc")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    // ── GET /api/v1/accounts/kyc/status ───────────────────────────────────────

    @Test
    void getKycStatus_shouldReturn200_withSubmittedTrue() throws Exception {
        when(accountService.hasKyc(userId)).thenReturn(true);

        mockMvc.perform(get("/api/v1/accounts/kyc/status")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submitted").value(true));
    }

    @Test
    void getKycStatus_shouldReturn200_withSubmittedFalse() throws Exception {
        when(accountService.hasKyc(userId)).thenReturn(false);

        mockMvc.perform(get("/api/v1/accounts/kyc/status")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submitted").value(false));
    }

    // ── POST /api/v1/accounts/{id}/credit — unauthorized ─────────────────────

    @Test
    void credit_shouldReturn401_whenInternalSecretInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/{id}/credit", accountId)
                        .header("X-Internal-Secret", "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", "50.00"))))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(accountService);
    }

    @Test
    void credit_shouldReturn401_whenXUserIdIsNotAValidUUID() throws Exception {
        // Sends a non-blank, non-UUID X-User-Id with no internal secret
        // → isValidUUID("not-a-uuid") throws IllegalArgumentException → caught → returns false → 401
        mockMvc.perform(post("/api/v1/accounts/{id}/credit", accountId)
                        .header("X-User-Id", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", "50.00"))))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(accountService);
    }

    @Test
    void credit_shouldReturn401_whenFilterPassesButSecretIsWrong() throws Exception {
        // Filter lets the request through (valid X-User-Id present) but controller's
        // own double-check rejects the wrong internal secret.
        mockMvc.perform(post("/api/v1/accounts/{id}/credit", accountId)
                        .header("X-User-Id", userId.toString())
                        .header("X-Internal-Secret", "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", "50.00"))))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(accountService);
    }

    // ── GlobalExceptionHandler — 500 générique ────────────────────────────────

    @Test
    void getMyAccounts_shouldReturn500_whenUnexpectedErrorOccurs() throws Exception {
        when(accountService.getMyAccounts(userId))
                .thenThrow(new RuntimeException("Unexpected database error"));

        mockMvc.perform(get("/api/v1/accounts")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500));
    }
}
