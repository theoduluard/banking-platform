package com.solarisbank.currency_service.controller;

import com.solarisbank.currency_service.model.ExchangeRate;
import com.solarisbank.currency_service.service.CurrencyService;
import com.solarisbank.currency_service.service.ExchangeRateRefreshService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/currencies")
@RequiredArgsConstructor
public class CurrencyController {

    private final CurrencyService currencyService;
    private final ExchangeRateRefreshService refreshService;

    /**
     * Returns exchange rates for a given base currency.
     *
     * Response shape expected by the frontend:
     * {
     *   "base": "EUR",
     *   "rates": { "USD": 1.08, "GBP": 0.85, … },
     *   "updated_at": "2026-06-14T10:00:00"
     * }
     */
    @GetMapping("/rates")
    public ResponseEntity<Map<String, Object>> getRates(
            @RequestParam(defaultValue = "EUR") String base) {

        List<ExchangeRate> rows = currencyService.getRatesForBase(base);

        // Collect target → rate pairs (preserve alphabetical order)
        Map<String, BigDecimal> rates = new LinkedHashMap<>();
        LocalDateTime latestUpdate = null;
        for (ExchangeRate r : rows) {
            rates.put(r.getTargetCurrency(), r.getRate());
            if (latestUpdate == null || r.getUpdatedAt().isAfter(latestUpdate)) {
                latestUpdate = r.getUpdatedAt();
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("base",       base.toUpperCase());
        body.put("rates",      rates);
        body.put("updated_at", latestUpdate != null ? latestUpdate.toString() : null);

        return ResponseEntity.ok(body);
    }

    /**
     * Converts an amount from one currency to another.
     *
     * Response shape expected by the frontend:
     * { "from", "to", "amount", "result", "rate" }
     */
    @GetMapping("/convert")
    public ResponseEntity<Map<String, Object>> convert(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam BigDecimal amount) {

        BigDecimal result = currencyService.convert(from, to, amount);
        BigDecimal rate   = currencyService.getRate(from, to);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from",   from.toUpperCase());
        body.put("to",     to.toUpperCase());
        body.put("amount", amount);
        body.put("result", result);
        body.put("rate",   rate);

        return ResponseEntity.ok(body);
    }

    // Admin — manually update a single rate
    @PutMapping("/admin/rates")
    public ResponseEntity<ExchangeRate> updateRate(@RequestBody Map<String, Object> body) {
        String base   = (String) body.get("base");
        String target = (String) body.get("target");
        BigDecimal rate = new BigDecimal(body.get("rate").toString());
        return ResponseEntity.ok(currencyService.updateRate(base, target, rate));
    }

    // Admin — force an immediate refresh from Frankfurter
    @PostMapping("/admin/refresh")
    public ResponseEntity<Map<String, String>> forceRefresh() {
        refreshService.refresh();
        return ResponseEntity.ok(Map.of("status", "refreshed"));
    }
}
