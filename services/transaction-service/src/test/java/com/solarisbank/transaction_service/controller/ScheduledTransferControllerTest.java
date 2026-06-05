package com.solarisbank.transaction_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.solarisbank.transaction_service.dto.ScheduledTransferRequest;
import com.solarisbank.transaction_service.dto.ScheduledTransferResponse;
import com.solarisbank.transaction_service.exception.BusinessException;
import com.solarisbank.transaction_service.model.ScheduledTransfer;
import com.solarisbank.transaction_service.service.ScheduledTransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScheduledTransferController.class)
class ScheduledTransferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScheduledTransferService scheduledTransferService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private UUID userId;
    private UUID transferId;
    private UUID fromAccountId;
    private UUID toAccountId;
    private ScheduledTransferResponse sampleResponse;

    @BeforeEach
    void setUp() {
        userId        = UUID.randomUUID();
        transferId    = UUID.randomUUID();
        fromAccountId = UUID.randomUUID();
        toAccountId   = UUID.randomUUID();

        sampleResponse = ScheduledTransferResponse.builder()
                .id(transferId)
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(new BigDecimal("150.00"))
                .currency("EUR")
                .description("Loyer")
                .frequency(ScheduledTransfer.Frequency.MONTHLY)
                .nextExecutionDate(LocalDate.now().plusDays(5))
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ── POST /api/v1/scheduled-transfers ──────────────────────────────────────

    @Test
    void create_shouldReturn201_withScheduledTransferResponse() throws Exception {
        when(scheduledTransferService.create(eq(userId), any(ScheduledTransferRequest.class)))
                .thenReturn(sampleResponse);

        ScheduledTransferRequest req = buildRequest();

        mockMvc.perform(post("/api/v1/scheduled-transfers")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(transferId.toString()))
                .andExpect(jsonPath("$.frequency").value("MONTHLY"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void create_shouldReturn400_whenFromAccountIdIsMissing() throws Exception {
        ScheduledTransferRequest req = buildRequest();
        req.setFromAccountId(null);

        mockMvc.perform(post("/api/v1/scheduled-transfers")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(scheduledTransferService);
    }

    @Test
    void create_shouldReturn400_whenAmountIsZero() throws Exception {
        ScheduledTransferRequest req = buildRequest();
        req.setAmount(BigDecimal.ZERO);

        mockMvc.perform(post("/api/v1/scheduled-transfers")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(scheduledTransferService);
    }

    @Test
    void create_shouldReturn400_whenSameAccounts() throws Exception {
        when(scheduledTransferService.create(eq(userId), any()))
                .thenThrow(new BusinessException("Source and destination accounts must differ",
                        HttpStatus.BAD_REQUEST));

        ScheduledTransferRequest req = buildRequest();

        mockMvc.perform(post("/api/v1/scheduled-transfers")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/v1/scheduled-transfers ───────────────────────────────────────

    @Test
    void getMyScheduledTransfers_shouldReturn200_withList() throws Exception {
        when(scheduledTransferService.getMyScheduledTransfers(userId))
                .thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/v1/scheduled-transfers")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(transferId.toString()))
                .andExpect(jsonPath("$[0].frequency").value("MONTHLY"));
    }

    @Test
    void getMyScheduledTransfers_shouldReturn200_withEmptyList() throws Exception {
        when(scheduledTransferService.getMyScheduledTransfers(userId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/scheduled-transfers")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── DELETE /api/v1/scheduled-transfers/{id} ───────────────────────────────

    @Test
    void cancel_shouldReturn204_whenSuccessful() throws Exception {
        doNothing().when(scheduledTransferService).cancel(transferId, userId);

        mockMvc.perform(delete("/api/v1/scheduled-transfers/{id}", transferId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNoContent());

        verify(scheduledTransferService).cancel(transferId, userId);
    }

    @Test
    void cancel_shouldReturn404_whenNotFound() throws Exception {
        doThrow(new BusinessException("Scheduled transfer not found", HttpStatus.NOT_FOUND))
                .when(scheduledTransferService).cancel(transferId, userId);

        mockMvc.perform(delete("/api/v1/scheduled-transfers/{id}", transferId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancel_shouldReturn403_whenAccessDenied() throws Exception {
        doThrow(new BusinessException("Access denied", HttpStatus.FORBIDDEN))
                .when(scheduledTransferService).cancel(transferId, userId);

        mockMvc.perform(delete("/api/v1/scheduled-transfers/{id}", transferId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private ScheduledTransferRequest buildRequest() {
        ScheduledTransferRequest req = new ScheduledTransferRequest();
        req.setFromAccountId(fromAccountId);
        req.setToAccountId(toAccountId);
        req.setAmount(new BigDecimal("150.00"));
        req.setDescription("Loyer");
        req.setFrequency(ScheduledTransfer.Frequency.MONTHLY);
        req.setFirstExecutionDate(LocalDate.now().plusDays(5));
        return req;
    }
}
