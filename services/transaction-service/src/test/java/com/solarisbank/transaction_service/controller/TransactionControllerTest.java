package com.solarisbank.transaction_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.solarisbank.transaction_service.dto.TransactionResponse;
import com.solarisbank.transaction_service.dto.TransferRequest;
import com.solarisbank.transaction_service.exception.BusinessException;
import com.solarisbank.transaction_service.model.Transaction;
import com.solarisbank.transaction_service.service.TransactionService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private UUID userId;
    private UUID fromAccountId;
    private UUID toAccountId;
    private UUID transactionId;
    private TransactionResponse pendingResponse;

    @BeforeEach
    void setUp() {
        userId        = UUID.randomUUID();
        fromAccountId = UUID.randomUUID();
        toAccountId   = UUID.randomUUID();
        transactionId = UUID.randomUUID();

        pendingResponse = TransactionResponse.builder()
                .id(transactionId)
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(new BigDecimal("100.00"))
                .currency("EUR")
                .type(Transaction.Type.TRANSFER)
                .status(Transaction.Status.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ── POST /api/v1/transactions/transfer ────────────────────────────────────

    @Test
    void transfer_shouldReturn202_withPendingTransaction() throws Exception {
        when(transactionService.transfer(eq(userId), any(TransferRequest.class)))
                .thenReturn(pendingResponse);

        TransferRequest request = new TransferRequest();
        request.setFromAccountId(fromAccountId);
        request.setToAccountId(toAccountId);
        request.setAmount(new BigDecimal("100.00"));

        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.id").value(transactionId.toString()));
    }

    @Test
    void transfer_shouldReturn400_whenAmountIsMissing() throws Exception {
        TransferRequest request = new TransferRequest();
        request.setFromAccountId(fromAccountId);
        request.setToAccountId(toAccountId);
        // amount manquant → @NotNull déclenche 400

        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/v1/transactions ───────────────────────────────────────────────

    @Test
    void getHistory_shouldReturn200_withPageOfTransactions() throws Exception {
        Page<TransactionResponse> page = new PageImpl<>(List.of(pendingResponse));
        when(transactionService.getHistory(fromAccountId, 0, 20)).thenReturn(page);

        mockMvc.perform(get("/api/v1/transactions")
                        .param("accountId", fromAccountId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(transactionId.toString()));
    }

    // ── GET /api/v1/transactions/{id} ──────────────────────────────────────────

    @Test
    void getTransaction_shouldReturn200_whenFound() throws Exception {
        when(transactionService.getTransaction(transactionId, userId)).thenReturn(pendingResponse);

        mockMvc.perform(get("/api/v1/transactions/{id}", transactionId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transactionId.toString()));
    }

    @Test
    void getTransaction_shouldReturn404_whenNotFound() throws Exception {
        when(transactionService.getTransaction(transactionId, userId))
                .thenThrow(new BusinessException("Transaction not found", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/v1/transactions/{id}", transactionId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTransaction_shouldReturn403_whenAccessDenied() throws Exception {
        when(transactionService.getTransaction(transactionId, userId))
                .thenThrow(new BusinessException("Access denied", HttpStatus.FORBIDDEN));

        mockMvc.perform(get("/api/v1/transactions/{id}", transactionId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isForbidden());
    }
}
