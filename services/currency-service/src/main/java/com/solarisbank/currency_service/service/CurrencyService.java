package com.solarisbank.currency_service.service;

import com.solarisbank.currency_service.model.ExchangeRate;
import com.solarisbank.currency_service.repository.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CurrencyService {

    private final ExchangeRateRepository repo;

    public List<ExchangeRate> getRatesForBase(String base) {
        return repo.findByBaseCurrencyOrderByTargetCurrencyAsc(base.toUpperCase());
    }

    public BigDecimal getRate(String from, String to) {
        if (from.equalsIgnoreCase(to)) return BigDecimal.ONE;
        return repo.findByBaseCurrencyAndTargetCurrency(from.toUpperCase(), to.toUpperCase())
                .map(ExchangeRate::getRate)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported currency pair: " + from + "/" + to));
    }

    public BigDecimal convert(String from, String to, BigDecimal amount) {
        if (from.equalsIgnoreCase(to)) return amount;
        BigDecimal rate = getRate(from, to);
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public ExchangeRate updateRate(String base, String target, BigDecimal rate) {
        ExchangeRate er = repo.findByBaseCurrencyAndTargetCurrency(base.toUpperCase(), target.toUpperCase())
                .orElseGet(() -> ExchangeRate.builder()
                        .baseCurrency(base.toUpperCase())
                        .targetCurrency(target.toUpperCase())
                        .build());
        er.setRate(rate);
        er.setUpdatedAt(LocalDateTime.now());
        return repo.save(er);
    }
}
