package com.solarisbank.transaction_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.solarisbank.transaction_service.dto.TransactionResponse;
import com.solarisbank.transaction_service.exception.BusinessException;
import com.solarisbank.transaction_service.model.Transaction;
import com.solarisbank.transaction_service.repository.TransactionRepository;
import com.solarisbank.transaction_service.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

@WebMvcTest(AdminTransactionController.class)
class AdminTransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionRepository transactionRepository;

    @MockitoBean
    private TransactionService transactionService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private UUID adminId;
    private UUID accountId;
    private UUID transactionId;
    private TransactionResponse sampleResponse;

    @BeforeEach
    void setUp() {
        adminId       = UUID.randomUUID();
        accountId     = UUID.randomUUID();
        transactionId = UUID.randomUUID();

        sampleResponse = TransactionResponse.builder()
                .id(transactionId)
                .fromAccountId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
                .toAccountId(accountId)
                .amount(new BigDecimal("500.00"))
                .currency("EUR")
                .type(Transaction.Type.DEPOSIT)
                .status(Transaction.Status.COMPLETED)
                .createdAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();
    }

    // ── GET /api/v1/admin/transactions ────────────────────────────────────────

    @Test
    void getAllTransactions_shouldReturn200_whenAdmin() throws Exception {
        Transaction tx = Transaction.builder()
                .id(transactionId)
                .fromAccountId(UUID.randomUUID()).toAccountId(accountId)
                .initiatedByUserId(adminId)
                .amount(new BigDecimal("100.00")).currency("EUR")
                .type(Transaction.Type.TRANSFER).status(Transaction.Status.COMPLETED)
                .createdAt(LocalDateTime.now()).build();

        Page<Transaction> page = new PageImpl<>(List.of(tx),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")), 1);
        when(transactionRepository.findAll(any(PageRequest.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/transactions")
                        .header("X-User-Id",   adminId.toString())
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(transactionId.toString()));
    }

    @Test
    void getAllTransactions_shouldReturn200_withPaginationParams() throws Exception {
        Page<Transaction> page = new PageImpl<>(List.of());
        when(transactionRepository.findAll(any(PageRequest.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/transactions")
                        .header("X-User-Id",   adminId.toString())
                        .header("X-User-Role", "ADMIN")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk());
    }

    @Test
    void getAllTransactions_shouldReturn403_whenNotAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/transactions")
                        .header("X-User-Id",   adminId.toString())
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(transactionService);
        verify(transactionRepository, never()).findAll(any(PageRequest.class));
    }

    // ── POST /api/v1/admin/transactions/deposit ───────────────────────────────

    @Test
    void adminDeposit_shouldReturn200_whenAdmin() throws Exception {
        when(transactionService.adminDeposit(eq(adminId), any())).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/admin/transactions/deposit")
                        .header("X-User-Id",   adminId.toString())
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "accountId",  accountId.toString(),
                                "amount",     "500.00",
                                "description","Dépôt initial"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transactionId.toString()))
                .andExpect(jsonPath("$.type").value("DEPOSIT"));
    }

    @Test
    void adminDeposit_shouldReturn403_whenNotAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/admin/transactions/deposit")
                        .header("X-User-Id",   adminId.toString())
                        .header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "accountId", accountId.toString(),
                                "amount",    "100.00"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(transactionService);
    }

    @Test
    void adminDeposit_shouldReturn400_whenAmountIsZero() throws Exception {
        mockMvc.perform(post("/api/v1/admin/transactions/deposit")
                        .header("X-User-Id",   adminId.toString())
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "accountId", accountId.toString(),
                                "amount",    "0.00"))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    void adminDeposit_shouldReturn404_whenAccountNotFound() throws Exception {
        when(transactionService.adminDeposit(eq(adminId), any()))
                .thenThrow(new BusinessException("Account not found", HttpStatus.NOT_FOUND));

        mockMvc.perform(post("/api/v1/admin/transactions/deposit")
                        .header("X-User-Id",   adminId.toString())
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "accountId", accountId.toString(),
                                "amount",    "100.00"))))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/v1/admin/transactions/withdrawal ────────────────────────────

    @Test
    void adminWithdrawal_shouldReturn200_whenAdmin() throws Exception {
        TransactionResponse withdrawalResponse = TransactionResponse.builder()
                .id(transactionId)
                .fromAccountId(accountId)
                .toAccountId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
                .amount(new BigDecimal("200.00")).currency("EUR")
                .type(Transaction.Type.WITHDRAWAL).status(Transaction.Status.COMPLETED)
                .createdAt(LocalDateTime.now()).completedAt(LocalDateTime.now()).build();

        when(transactionService.adminWithdrawal(eq(adminId), any())).thenReturn(withdrawalResponse);

        mockMvc.perform(post("/api/v1/admin/transactions/withdrawal")
                        .header("X-User-Id",   adminId.toString())
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "accountId", accountId.toString(),
                                "amount",    "200.00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("WITHDRAWAL"));
    }

    @Test
    void adminWithdrawal_shouldReturn403_whenNotAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/admin/transactions/withdrawal")
                        .header("X-User-Id",   adminId.toString())
                        .header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "accountId", accountId.toString(),
                                "amount",    "50.00"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(transactionService);
    }

    @Test
    void adminWithdrawal_shouldReturn405_whenInsufficientFunds() throws Exception {
        when(transactionService.adminWithdrawal(eq(adminId), any()))
                .thenThrow(new BusinessException("Insufficient funds", HttpStatus.METHOD_NOT_ALLOWED));

        mockMvc.perform(post("/api/v1/admin/transactions/withdrawal")
                        .header("X-User-Id",   adminId.toString())
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "accountId", accountId.toString(),
                                "amount",    "99999.00"))))
                .andExpect(status().isMethodNotAllowed());
    }
}
