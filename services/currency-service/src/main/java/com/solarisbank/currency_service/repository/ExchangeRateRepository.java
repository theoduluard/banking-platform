package com.solarisbank.currency_service.repository;

import com.solarisbank.currency_service.model.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {
    Optional<ExchangeRate> findByBaseCurrencyAndTargetCurrency(String base, String target);
    List<ExchangeRate> findByBaseCurrencyOrderByTargetCurrencyAsc(String base);
}
