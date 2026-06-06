package com.solarisbank.card_service.controller;

import com.solarisbank.card_service.dto.CardResponse;
import com.solarisbank.card_service.dto.CreateCardRequest;
import com.solarisbank.card_service.service.CardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;

    @PostMapping
    public ResponseEntity<CardResponse> createCard(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody CreateCardRequest req) {
        return ResponseEntity.ok(cardService.createCard(userId, req));
    }

    @GetMapping
    public ResponseEntity<List<CardResponse>> getCards(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(cardService.getCardsForUser(userId));
    }

    @PostMapping("/{id}/freeze")
    public ResponseEntity<CardResponse> freeze(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(cardService.freezeCard(id, userId));
    }

    @PostMapping("/{id}/unfreeze")
    public ResponseEntity<CardResponse> unfreeze(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(cardService.unfreezeCard(id, userId));
    }

    @PutMapping("/{id}/limit")
    public ResponseEntity<CardResponse> updateLimit(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody Map<String, BigDecimal> body) {
        return ResponseEntity.ok(cardService.updateLimit(id, userId, body.get("limit")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelCard(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId) {
        cardService.cancelCard(id, userId);
        return ResponseEntity.noContent().build();
    }
}
