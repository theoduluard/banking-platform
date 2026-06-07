package com.solarisbank.card_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solarisbank.card_service.dto.CardResponse;
import com.solarisbank.card_service.dto.CreateCardRequest;
import com.solarisbank.card_service.service.CardService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CardController.class)
class CardControllerTest {

    @Autowired MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean CardService cardService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CARD_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();

    private CardResponse buildResponse() {
        return CardResponse.builder()
                .id(CARD_ID)
                .accountId(ACCOUNT_ID)
                .maskedNumber("**** **** **** 1234")
                .cardholderName("John Doe")
                .cardType("VIRTUAL")
                .status("ACTIVE")
                .expiryMonth((short) 6)
                .expiryYear((short) 2029)
                .spendingLimit(new BigDecimal("1000.00"))
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void createCard_shouldReturn200() throws Exception {
        CreateCardRequest req = new CreateCardRequest();
        req.setAccountId(ACCOUNT_ID);
        req.setCardType("VIRTUAL");
        req.setCardholderName("John Doe");

        when(cardService.createCard(eq(USER_ID), any(CreateCardRequest.class)))
                .thenReturn(buildResponse());

        mockMvc.perform(post("/api/v1/cards")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardType").value("VIRTUAL"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.maskedNumber").value("**** **** **** 1234"));
    }

    @Test
    void getCards_shouldReturn200WithList() throws Exception {
        when(cardService.getCardsForUser(USER_ID)).thenReturn(List.of(buildResponse()));

        mockMvc.perform(get("/api/v1/cards")
                        .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void freezeCard_shouldReturn200() throws Exception {
        CardResponse frozen = CardResponse.builder()
                .id(CARD_ID).status("FROZEN").cardType("VIRTUAL")
                .maskedNumber("**** **** **** 1234").cardholderName("John")
                .accountId(ACCOUNT_ID).expiryMonth((short)6).expiryYear((short)2029)
                .createdAt(LocalDateTime.now()).build();
        when(cardService.freezeCard(CARD_ID, USER_ID)).thenReturn(frozen);

        mockMvc.perform(post("/api/v1/cards/{id}/freeze", CARD_ID)
                        .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));
    }

    @Test
    void unfreezeCard_shouldReturn200() throws Exception {
        when(cardService.unfreezeCard(CARD_ID, USER_ID)).thenReturn(buildResponse());

        mockMvc.perform(post("/api/v1/cards/{id}/unfreeze", CARD_ID)
                        .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void updateLimit_shouldReturn200() throws Exception {
        when(cardService.updateLimit(eq(CARD_ID), eq(USER_ID), any(BigDecimal.class)))
                .thenReturn(buildResponse());

        mockMvc.perform(put("/api/v1/cards/{id}/limit", CARD_ID)
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("limit", "500.00"))))
                .andExpect(status().isOk());
    }

    @Test
    void cancelCard_shouldReturn204() throws Exception {
        doNothing().when(cardService).cancelCard(CARD_ID, USER_ID);

        mockMvc.perform(delete("/api/v1/cards/{id}", CARD_ID)
                        .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isNoContent());
    }
}
