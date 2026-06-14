package com.solarisbank.currency_service.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fetches live exchange rates from Frankfurter (https://api.frankfurter.app).
 *
 * Strategy — one API call, all cross-rates derived locally:
 *   1. GET /latest?base=EUR  → rates EUR/X for every supported currency X
 *   2. Persist each EUR/X pair directly.
 *   3. Persist each X/EUR inverse  = 1 / rate(EUR/X).
 *   4. Derive every cross-rate A/B = rate(EUR/B) / rate(EUR/A).
 *
 * The maths hold because Frankfurter uses mid-market rates from the ECB,
 * so all rates are internally consistent through EUR.
 *
 * Refresh schedule: once at startup, then every 6 hours.
 * Frankfurter updates daily from ECB; 6h is a reasonable polling interval
 * that avoids hammering the free service while keeping rates fresh.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateRefreshService {

    /** Currencies we track — must be a subset of what Frankfurter exposes. */
    private static final List<String> SUPPORTED = List.of(
            "USD", "GBP", "CHF", "JPY", "CAD",
            "AUD", "CNY", "NOK", "SEK", "DKK",
            "PLN", "HUF", "SGD", "HKD", "MXN",
            "BRL", "INR", "TRY", "ZAR", "CZK"
    );

    private final CurrencyService currencyService;

    private final RestClient restClient = RestClient.create();

    @Value("${exchange.api.url:https://api.frankfurter.dev/v1}")
    private String apiUrl;

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    /** Warm the DB immediately so the service is usable the moment it starts. */
    @PostConstruct
    public void refreshOnStartup() {
        log.info("[Currency] Fetching initial rates from Frankfurter…");
        refresh();
    }

    /** Re-fetch every 6 hours (ECB publishes once a day; 6h is a safe margin). */
    @Scheduled(cron = "0 0 */6 * * *")
    public void scheduledRefresh() {
        log.info("[Currency] Scheduled rate refresh from Frankfurter");
        refresh();
    }

    // ── Core logic ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public void refresh() {
        try {
            // Single call: all ECB rates relative to EUR
            Map<String, Object> body = restClient.get()
                    .uri(apiUrl + "/latest?base=EUR")
                    .retrieve()
                    .body(Map.class);

            if (body == null || !(body.get("rates") instanceof Map<?, ?> raw)) {
                log.warn("[Currency] Unexpected Frankfurter response — skipping refresh");
                return;
            }

            // Keep only the currencies we actually support
            Map<String, BigDecimal> eurRates = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                String code = entry.getKey().toString();
                if (SUPPORTED.contains(code)) {
                    eurRates.put(code, new BigDecimal(entry.getValue().toString()));
                }
            }

            int saved = 0;

            for (Map.Entry<String, BigDecimal> entry : eurRates.entrySet()) {
                String target = entry.getKey();
                BigDecimal rate = entry.getValue();

                // EUR → target
                currencyService.updateRate("EUR", target, rate.setScale(8, RoundingMode.HALF_UP));

                // target → EUR  (inverse)
                BigDecimal inverse = BigDecimal.ONE.divide(rate, 8, RoundingMode.HALF_UP);
                currencyService.updateRate(target, "EUR", inverse);

                saved += 2;
            }

            // Cross-rates: A → B = rate(EUR→B) / rate(EUR→A)
            List<String> codes = List.copyOf(eurRates.keySet());
            for (int i = 0; i < codes.size(); i++) {
                String from    = codes.get(i);
                BigDecimal rFrom = eurRates.get(from);   // EUR/from

                for (int j = 0; j < codes.size(); j++) {
                    if (i == j) continue;
                    String to      = codes.get(j);
                    BigDecimal rTo = eurRates.get(to);   // EUR/to

                    // from → to = (EUR/to) / (EUR/from)
                    BigDecimal cross = rTo.divide(rFrom, 8, RoundingMode.HALF_UP);
                    currencyService.updateRate(from, to, cross);
                    saved++;
                }
            }

            log.info("[Currency] Refreshed {} rate pairs (base: EUR, sources: {})",
                    saved, eurRates.size());

        } catch (Exception e) {
            log.error("[Currency] Rate refresh failed — keeping existing DB rates: {}", e.getMessage());
        }
    }
}
