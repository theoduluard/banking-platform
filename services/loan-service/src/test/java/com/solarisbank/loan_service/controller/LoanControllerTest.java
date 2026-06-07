package com.solarisbank.loan_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solarisbank.loan_service.dto.LoanApplicationRequest;
import com.solarisbank.loan_service.dto.LoanSimulationRequest;
import com.solarisbank.loan_service.dto.LoanSimulationResponse;
import com.solarisbank.loan_service.model.Loan;
import com.solarisbank.loan_service.service.LoanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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

@WebMvcTest(controllers = LoanController.class)
class LoanControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean LoanService loanService;

    private static final UUID USER_ID    = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID LOAN_ID    = UUID.randomUUID();

    @Test
    void simulate_shouldReturn200WithSimulationData() throws Exception {
        LoanSimulationRequest req = new LoanSimulationRequest();
        req.setAmount(new BigDecimal("10000"));
        req.setDurationMonths(36);

        LoanSimulationResponse resp = LoanSimulationResponse.builder()
                .amount(new BigDecimal("10000"))
                .durationMonths(36)
                .interestRate(new BigDecimal("5.50"))
                .monthlyPayment(new BigDecimal("300.96"))
                .totalRepayment(new BigDecimal("10834.56"))
                .totalInterest(new BigDecimal("834.56"))
                .build();

        when(loanService.simulate(any(BigDecimal.class), eq(36))).thenReturn(resp);

        mockMvc.perform(post("/api/v1/loans/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interestRate").value(5.50))
                .andExpect(jsonPath("$.durationMonths").value(36));
    }

    @Test
    void apply_shouldReturn200WithCreatedLoan() throws Exception {
        LoanApplicationRequest req = new LoanApplicationRequest();
        req.setAmount(new BigDecimal("5000"));
        req.setDurationMonths(24);
        req.setAccountId(ACCOUNT_ID);
        req.setPurpose("Car");

        Loan loan = Loan.builder()
                .id(LOAN_ID).userId(USER_ID).accountId(ACCOUNT_ID)
                .amount(new BigDecimal("5000")).durationMonths(24)
                .interestRate(new BigDecimal("5.50"))
                .monthlyPayment(new BigDecimal("220.00"))
                .totalRepayment(new BigDecimal("5280.00"))
                .status(Loan.LoanStatus.PENDING)
                .purpose("Car")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();

        when(loanService.apply(eq(USER_ID), any(LoanApplicationRequest.class))).thenReturn(loan);

        mockMvc.perform(post("/api/v1/loans")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getMyLoans_shouldReturn200WithList() throws Exception {
        Loan loan = Loan.builder()
                .id(LOAN_ID).userId(USER_ID).accountId(ACCOUNT_ID)
                .amount(new BigDecimal("5000")).durationMonths(24)
                .interestRate(new BigDecimal("5.50"))
                .monthlyPayment(new BigDecimal("220.00"))
                .totalRepayment(new BigDecimal("5280.00"))
                .status(Loan.LoanStatus.APPROVED)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();

        when(loanService.getUserLoans(USER_ID)).thenReturn(List.of(loan));

        mockMvc.perform(get("/api/v1/loans")
                        .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("APPROVED"));
    }
}
