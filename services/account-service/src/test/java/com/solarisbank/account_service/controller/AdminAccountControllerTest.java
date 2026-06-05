package com.solarisbank.account_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.solarisbank.account_service.dto.AccountResponse;
import com.solarisbank.account_service.dto.CreditRequest;
import com.solarisbank.account_service.exception.BusinessException;
import com.solarisbank.account_service.model.Account;
import com.solarisbank.account_service.repository.AccountRepository;
import com.solarisbank.account_service.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminAccountController.class)
class AdminAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountRepository accountRepository;

    @MockitoBean
    private AccountService accountService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private UUID accountId;
    private UUID adminUserId;
    private AccountResponse activeAccountResponse;

    @BeforeEach
    void setUp() {
        accountId   = UUID.randomUUID();
        adminUserId = UUID.randomUUID();

        activeAccountResponse = AccountResponse.builder()
                .id(accountId)
                .userId(UUID.randomUUID())
                .iban("FR7630006000011234567890189")
                .type(Account.Type.CHECKING)
                .balance(new BigDecimal("500.00"))
                .currency("EUR")
                .status(Account.Status.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ── GET /api/v1/admin/accounts/pending ────────────────────────────────────

    @Test
    void getPendingAccounts_shouldReturn200_withList() throws Exception {
        when(accountService.getPendingAccounts()).thenReturn(List.of(activeAccountResponse));

        mockMvc.perform(get("/api/v1/admin/accounts/pending")
                        .header("X-User-Id", adminUserId.toString())
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(accountId.toString()));
    }

    @Test
    void getPendingAccounts_shouldReturn403_whenNotAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/accounts/pending")
                        .header("X-User-Id", adminUserId.toString())
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(accountService);
    }

    // ── POST /api/v1/admin/accounts/{id}/approve ──────────────────────────────

    @Test
    void approveAccount_shouldReturn200() throws Exception {
        when(accountService.approveAccount(accountId)).thenReturn(activeAccountResponse);

        mockMvc.perform(post("/api/v1/admin/accounts/{id}/approve", accountId)
                        .header("X-User-Id", adminUserId.toString())
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accountId.toString()));
    }

    @Test
    void approveAccount_shouldReturn404_whenNotFound() throws Exception {
        when(accountService.approveAccount(accountId))
                .thenThrow(new BusinessException("Account not found", HttpStatus.NOT_FOUND));

        mockMvc.perform(post("/api/v1/admin/accounts/{id}/approve", accountId)
                        .header("X-User-Id", adminUserId.toString())
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/v1/admin/accounts/{id}/reject ───────────────────────────────

    @Test
    void rejectAccount_shouldReturn200() throws Exception {
        AccountResponse rejectedResponse = AccountResponse.builder()
                .id(accountId).status(Account.Status.REJECTED)
                .iban("FR76300060000112").currency("EUR")
                .type(Account.Type.CHECKING)
                .balance(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .userId(UUID.randomUUID())
                .build();

        when(accountService.rejectAccount(accountId)).thenReturn(rejectedResponse);

        mockMvc.perform(post("/api/v1/admin/accounts/{id}/reject", accountId)
                        .header("X-User-Id", adminUserId.toString())
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    // ── POST /api/v1/admin/accounts/{id}/deposit ──────────────────────────────

    @Test
    void adminDeposit_shouldReturn200() throws Exception {
        when(accountService.adminDeposit(eq(accountId), any(BigDecimal.class)))
                .thenReturn(activeAccountResponse);

        CreditRequest request = new CreditRequest();
        request.setAmount(new BigDecimal("100.00"));

        mockMvc.perform(post("/api/v1/admin/accounts/{id}/deposit", accountId)
                        .header("X-User-Id", adminUserId.toString())
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accountId.toString()));
    }

    @Test
    void adminDeposit_shouldReturn400_whenAmountIsZero() throws Exception {
        CreditRequest request = new CreditRequest();
        request.setAmount(BigDecimal.ZERO);

        mockMvc.perform(post("/api/v1/admin/accounts/{id}/deposit", accountId)
                        .header("X-User-Id", adminUserId.toString())
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    void adminDeposit_shouldReturn400_whenAmountIsNull() throws Exception {
        CreditRequest request = new CreditRequest();
        // amount intentionally left null

        mockMvc.perform(post("/api/v1/admin/accounts/{id}/deposit", accountId)
                        .header("X-User-Id", adminUserId.toString())
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/v1/admin/accounts/{id}/withdrawal ───────────────────────────

    @Test
    void adminWithdrawal_shouldReturn200() throws Exception {
        when(accountService.adminWithdrawal(eq(accountId), any(BigDecimal.class)))
                .thenReturn(activeAccountResponse);

        CreditRequest request = new CreditRequest();
        request.setAmount(new BigDecimal("50.00"));

        mockMvc.perform(post("/api/v1/admin/accounts/{id}/withdrawal", accountId)
                        .header("X-User-Id", adminUserId.toString())
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void adminWithdrawal_shouldReturn400_whenAmountIsNegative() throws Exception {
        CreditRequest request = new CreditRequest();
        request.setAmount(new BigDecimal("-10.00"));

        mockMvc.perform(post("/api/v1/admin/accounts/{id}/withdrawal", accountId)
                        .header("X-User-Id", adminUserId.toString())
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
