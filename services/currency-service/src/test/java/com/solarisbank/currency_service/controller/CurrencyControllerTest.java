package com.solarisbank.currency_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solarisbank.currency_service.model.ExchangeRate;
import com.solarisbank.currency_service.service.CurrencyService;
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

@WebMvcTest(controllers = CurrencyController.class)
class CurrencyControllerTest {

    @Autowired MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @MockitoBean CurrencyService currencyService;

    private ExchangeRate buildRate(String base, String target, String rate) {
        return ExchangeRate.builder()
                .id(UUID.randomUUID())
                .baseCurrency(base).targetCurrency(target)
                .rate(new BigDecimal(rate))
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void getRates_shouldReturn200WithRates() throws Exception {
        when(currencyService.getRatesForBase("EUR"))
                .thenReturn(List.of(buildRate("EUR", "USD", "1.085")));

        mockMvc.perform(get("/api/v1/currencies/rates").param("base", "EUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].baseCurrency").value("EUR"))
                .andExpect(jsonPath("$[0].targetCurrency").value("USD"));
    }

    @Test
    void getRates_defaultBase_shouldUseEUR() throws Exception {
        when(currencyService.getRatesForBase("EUR")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/currencies/rates"))
                .andExpect(status().isOk());
    }

    @Test
    void convert_shouldReturn200WithResult() throws Exception {
        when(currencyService.convert("EUR", "USD", new BigDecimal("100")))
                .thenReturn(new BigDecimal("108.50"));

        mockMvc.perform(get("/api/v1/currencies/convert")
                        .param("from", "EUR")
                        .param("to", "USD")
                        .param("amount", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("EUR"))
                .andExpect(jsonPath("$.to").value("USD"))
                .andExpect(jsonPath("$.amount").value(100))
                .andExpect(jsonPath("$.result").value(108.50));
    }

    @Test
    void updateRate_shouldReturn200WithUpdatedRate() throws Exception {
        ExchangeRate updated = buildRate("EUR", "USD", "1.10");
        when(currencyService.updateRate(eq("EUR"), eq("USD"), any(BigDecimal.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/v1/currencies/admin/rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("base", "EUR", "target", "USD", "rate", "1.10"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency").value("EUR"))
                .andExpect(jsonPath("$.targetCurrency").value("USD"));
    }
}
