package com.solarisbank.fraud_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solarisbank.fraud_service.model.FraudAlert;
import com.solarisbank.fraud_service.repository.FraudAlertRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = FraudController.class)
class FraudControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean FraudAlertRepository repo;

    private static final UUID USER_ID  = UUID.randomUUID();
    private static final UUID ALERT_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();

    private FraudAlert buildAlert(FraudAlert.AlertStatus status) {
        return FraudAlert.builder()
                .id(ALERT_ID)
                .transactionId(UUID.randomUUID())
                .userId(USER_ID)
                .accountId(UUID.randomUUID())
                .amount(new BigDecimal("15000"))
                .ruleTriggered("HIGH_AMOUNT")
                .riskScore((short) 75)
                .status(status)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void getMyAlerts_shouldReturn200WithAlerts() throws Exception {
        when(repo.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(buildAlert(FraudAlert.AlertStatus.OPEN)));

        mockMvc.perform(get("/api/v1/fraud/alerts")
                        .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ruleTriggered").value("HIGH_AMOUNT"))
                .andExpect(jsonPath("$[0].riskScore").value(75));
    }

    @Test
    void getMyAlerts_empty_shouldReturn200EmptyList() throws Exception {
        when(repo.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/fraud/alerts")
                        .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getAllOpen_shouldReturn200WithOpenAlerts() throws Exception {
        when(repo.findByStatusOrderByCreatedAtDesc(FraudAlert.AlertStatus.OPEN))
                .thenReturn(List.of(buildAlert(FraudAlert.AlertStatus.OPEN)));

        mockMvc.perform(get("/api/v1/admin/fraud/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    @Test
    void resolve_shouldReturn200WithResolvedAlert() throws Exception {
        FraudAlert alert = buildAlert(FraudAlert.AlertStatus.OPEN);
        when(repo.findById(ALERT_ID)).thenReturn(Optional.of(alert));
        when(repo.save(any(FraudAlert.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/admin/fraud/alerts/{id}/resolve", ALERT_ID)
                        .header("X-User-Id", ADMIN_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("resolution", "RESOLVED", "note", "Confirmed legit"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void resolve_falsePositive_shouldSetFalsePositiveStatus() throws Exception {
        FraudAlert alert = buildAlert(FraudAlert.AlertStatus.OPEN);
        when(repo.findById(ALERT_ID)).thenReturn(Optional.of(alert));
        when(repo.save(any(FraudAlert.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/admin/fraud/alerts/{id}/resolve", ALERT_ID)
                        .header("X-User-Id", ADMIN_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("resolution", "FALSE_POSITIVE", "note", "Not fraud"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FALSE_POSITIVE"));
    }
}
