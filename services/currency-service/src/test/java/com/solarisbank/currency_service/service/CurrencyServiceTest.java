package com.solarisbank.currency_service.service;

import com.solarisbank.currency_service.model.ExchangeRate;
import com.solarisbank.currency_service.repository.ExchangeRateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CurrencyServiceTest {

    @Mock ExchangeRateRepository repo;
    @InjectMocks CurrencyService currencyService;

    private ExchangeRate buildRate(String base, String target, String rate) {
        return ExchangeRate.builder()
                .id(UUID.randomUUID())
                .baseCurrency(base)
                .targetCurrency(target)
                .rate(new BigDecimal(rate))
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ── getRatesForBase ────────────────────────────────────────────────────────

    @Test
    void getRatesForBase_shouldPassUppercaseToRepo() {
        List<ExchangeRate> rates = List.of(buildRate("EUR", "USD", "1.085"));
        when(repo.findByBaseCurrencyOrderByTargetCurrencyAsc("EUR")).thenReturn(rates);

        List<ExchangeRate> result = currencyService.getRatesForBase("eur");

        verify(repo).findByBaseCurrencyOrderByTargetCurrencyAsc("EUR");
        assertThat(result).hasSize(1);
    }

    @Test
    void getRatesForBase_alreadyUppercase_shouldWork() {
        when(repo.findByBaseCurrencyOrderByTargetCurrencyAsc("USD")).thenReturn(List.of());
        currencyService.getRatesForBase("USD");
        verify(repo).findByBaseCurrencyOrderByTargetCurrencyAsc("USD");
    }

    // ── convert ────────────────────────────────────────────────────────────────

    @Test
    void convert_sameCurrency_shouldReturnAmountUnchanged() {
        BigDecimal result = currencyService.convert("EUR", "eur", new BigDecimal("100"));
        assertThat(result).isEqualByComparingTo("100");
        verify(repo, never()).findByBaseCurrencyAndTargetCurrency(any(), any());
    }

    @Test
    void convert_differentCurrencies_shouldApplyRate() {
        when(repo.findByBaseCurrencyAndTargetCurrency("EUR", "USD"))
                .thenReturn(Optional.of(buildRate("EUR", "USD", "1.085")));

        BigDecimal result = currencyService.convert("eur", "usd", new BigDecimal("100"));

        assertThat(result).isEqualByComparingTo("108.50");
    }

    @Test
    void convert_unknownPair_shouldThrow() {
        when(repo.findByBaseCurrencyAndTargetCurrency("EUR", "XYZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> currencyService.convert("EUR", "XYZ", new BigDecimal("100")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported currency pair");
    }

    @Test
    void convert_resultRoundedToTwoDecimals() {
        when(repo.findByBaseCurrencyAndTargetCurrency("EUR", "JPY"))
                .thenReturn(Optional.of(buildRate("EUR", "JPY", "161.234567")));

        BigDecimal result = currencyService.convert("EUR", "JPY", new BigDecimal("10"));

        assertThat(result.scale()).isEqualTo(2);
    }

    // ── updateRate ─────────────────────────────────────────────────────────────

    @Test
    void updateRate_existingPair_shouldUpdateRateAndTimestamp() {
        ExchangeRate existing = buildRate("EUR", "USD", "1.05");
        when(repo.findByBaseCurrencyAndTargetCurrency("EUR", "USD")).thenReturn(Optional.of(existing));
        when(repo.save(any(ExchangeRate.class))).thenAnswer(inv -> inv.getArgument(0));

        ExchangeRate result = currencyService.updateRate("EUR", "USD", new BigDecimal("1.10"));

        assertThat(result.getRate()).isEqualByComparingTo("1.10");
        assertThat(result.getUpdatedAt()).isNotNull();
    }

    @Test
    void updateRate_newPair_shouldCreateNewExchangeRate() {
        when(repo.findByBaseCurrencyAndTargetCurrency("GBP", "CHF")).thenReturn(Optional.empty());
        when(repo.save(any(ExchangeRate.class))).thenAnswer(inv -> inv.getArgument(0));

        currencyService.updateRate("gbp", "chf", new BigDecimal("1.12"));

        ArgumentCaptor<ExchangeRate> captor = ArgumentCaptor.forClass(ExchangeRate.class);
        verify(repo).save(captor.capture());
        ExchangeRate saved = captor.getValue();

        assertThat(saved.getBaseCurrency()).isEqualTo("GBP");
        assertThat(saved.getTargetCurrency()).isEqualTo("CHF");
        assertThat(saved.getRate()).isEqualByComparingTo("1.12");
    }
}
