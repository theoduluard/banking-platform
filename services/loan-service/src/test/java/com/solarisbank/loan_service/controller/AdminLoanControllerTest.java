package com.solarisbank.loan_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminLoanController.class)
class AdminLoanControllerTest {

    @Autowired MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @MockitoBean LoanService loanService;

    private static final UUID LOAN_ID    = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID USER_ID    = UUID.randomUUID();

    private Loan buildLoan(Loan.LoanStatus status) {
        return Loan.builder()
                .id(LOAN_ID).userId(USER_ID).accountId(ACCOUNT_ID)
                .amount(new BigDecimal("8000")).durationMonths(48)
                .interestRate(new BigDecimal("5.50"))
                .monthlyPayment(new BigDecimal("185.00"))
                .totalRepayment(new BigDecimal("8880.00"))
                .status(status)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void getPending_shouldReturn200WithList() throws Exception {
        when(loanService.getPendingLoans()).thenReturn(List.of(buildLoan(Loan.LoanStatus.PENDING)));

        mockMvc.perform(get("/api/v1/admin/loans/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void decide_approve_shouldReturn200WithApprovedLoan() throws Exception {
        when(loanService.approveOrReject(eq(LOAN_ID), eq(true), any()))
                .thenReturn(buildLoan(Loan.LoanStatus.APPROVED));

        mockMvc.perform(post("/api/v1/admin/loans/{id}/decision", LOAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("action", "APPROVE", "note", "OK"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void decide_reject_shouldReturn200WithRejectedLoan() throws Exception {
        when(loanService.approveOrReject(eq(LOAN_ID), eq(false), any()))
                .thenReturn(buildLoan(Loan.LoanStatus.REJECTED));

        mockMvc.perform(post("/api/v1/admin/loans/{id}/decision", LOAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("action", "REJECT", "note", "Too risky"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }
}
