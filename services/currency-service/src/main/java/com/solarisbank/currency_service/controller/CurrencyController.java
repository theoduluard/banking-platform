package com.solarisbank.currency_service.controller;

import com.solarisbank.currency_service.model.ExchangeRate;
import com.solarisbank.currency_service.service.CurrencyService;
import com.solarisbank.currency_service.service.ExchangeRateRefreshService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/currencies")
@RequiredArgsConstructor
public class CurrencyController {

    private final CurrencyService currencyService;
    private final ExchangeRateRefreshService refreshService;

    @GetMapping("/rates")
    public ResponseEntity<List<ExchangeRate>> getRates(
            @RequestParam(defaultValue = "EUR") String base) {
        return ResponseEntity.ok(currencyService.getRatesForBase(base));
    }

    @GetMapping("/convert")
    public ResponseEntity<Map<String, Object>> convert(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam BigDecimal amount) {
        BigDecimal result = currencyService.convert(from, to, amount);
        return ResponseEntity.ok(Map.of(
                "from", from.toUpperCase(),
                "to", to.toUpperCase(),
                "amount", amount,
                "result", result
        ));
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
