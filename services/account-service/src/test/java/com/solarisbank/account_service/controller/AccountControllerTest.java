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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", "200.00"))))
                .andExpect(status().isNoContent());
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
