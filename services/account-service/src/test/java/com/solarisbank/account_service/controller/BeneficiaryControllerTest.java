package com.solarisbank.account_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.solarisbank.account_service.dto.BeneficiaryResponse;
import com.solarisbank.account_service.exception.BusinessException;
import com.solarisbank.account_service.service.BeneficiaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BeneficiaryController.class)
class BeneficiaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BeneficiaryService beneficiaryService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private UUID userId;
    private UUID beneficiaryId;
    private BeneficiaryResponse sampleResponse;

    @BeforeEach
    void setUp() {
        userId        = UUID.randomUUID();
        beneficiaryId = UUID.randomUUID();

        sampleResponse = BeneficiaryResponse.builder()
                .id(beneficiaryId)
                .name("Papa")
                .iban("FR7630006000011234567890189")
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ── GET /api/v1/beneficiaries ─────────────────────────────────────────────

    @Test
    void getAll_shouldReturn200_withBeneficiaryList() throws Exception {
        when(beneficiaryService.getAll(userId)).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/v1/beneficiaries")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(beneficiaryId.toString()))
                .andExpect(jsonPath("$[0].name").value("Papa"))
                .andExpect(jsonPath("$[0].iban").value("FR7630006000011234567890189"));
    }

    @Test
    void getAll_shouldReturn200_withEmptyList() throws Exception {
        when(beneficiaryService.getAll(userId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/beneficiaries")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── POST /api/v1/beneficiaries ────────────────────────────────────────────

    @Test
    void add_shouldReturn201_withBeneficiaryResponse() throws Exception {
        when(beneficiaryService.add(eq(userId), any())).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/beneficiaries")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("name", "Papa", "iban", "FR7630006000011234567890189"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(beneficiaryId.toString()))
                .andExpect(jsonPath("$.name").value("Papa"));
    }

    @Test
    void add_shouldReturn400_whenNameIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/beneficiaries")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("iban", "FR7630006000011234567890189"))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(beneficiaryService);
    }

    @Test
    void add_shouldReturn400_whenIbanFormatInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/beneficiaries")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("name", "Papa", "iban", "invalid-iban"))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(beneficiaryService);
    }

    @Test
    void add_shouldReturn409_whenIbanAlreadyExists() throws Exception {
        when(beneficiaryService.add(eq(userId), any()))
                .thenThrow(new BusinessException("This IBAN is already in your beneficiaries", HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/v1/beneficiaries")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("name", "Papa", "iban", "FR7630006000011234567890189"))))
                .andExpect(status().isConflict());
    }

    // ── DELETE /api/v1/beneficiaries/{id} ─────────────────────────────────────

    @Test
    void delete_shouldReturn204_whenSuccessful() throws Exception {
        doNothing().when(beneficiaryService).delete(userId, beneficiaryId);

        mockMvc.perform(delete("/api/v1/beneficiaries/{id}", beneficiaryId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNoContent());

        verify(beneficiaryService).delete(userId, beneficiaryId);
    }

    @Test
    void delete_shouldReturn404_whenNotFound() throws Exception {
        doThrow(new BusinessException("Beneficiary not found", HttpStatus.NOT_FOUND))
                .when(beneficiaryService).delete(userId, beneficiaryId);

        mockMvc.perform(delete("/api/v1/beneficiaries/{id}", beneficiaryId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_shouldReturn403_whenAccessDenied() throws Exception {
        doThrow(new BusinessException("Access denied", HttpStatus.FORBIDDEN))
                .when(beneficiaryService).delete(userId, beneficiaryId);

        mockMvc.perform(delete("/api/v1/beneficiaries/{id}", beneficiaryId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isForbidden());
    }
}
