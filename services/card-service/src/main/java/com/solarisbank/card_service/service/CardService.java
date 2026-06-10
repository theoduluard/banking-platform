package com.solarisbank.card_service.service;

import com.solarisbank.card_service.client.AccountClient;
import com.solarisbank.card_service.client.AccountResponse;
import com.solarisbank.card_service.dto.CardResponse;
import com.solarisbank.card_service.dto.CreateCardRequest;
import com.solarisbank.card_service.exception.BusinessException;
import com.solarisbank.card_service.model.Card;
import com.solarisbank.card_service.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CardService {

    private final CardRepository cardRepository;
    private final AccountClient accountClient;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public CardResponse createCard(UUID userId, CreateCardRequest req) {
        // Verify the account exists, belongs to the user, and is a CHECKING account.
        AccountResponse account = accountClient.getAccount(req.getAccountId(), userId);
        if (!"CHECKING".equals(account.getType())) {
            throw new BusinessException(
                "Cards can only be issued for CHECKING accounts. " +
                "Account " + req.getAccountId() + " is of type " + account.getType() + ".",
                HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new BusinessException(
                "Cards can only be issued for ACTIVE accounts. " +
                "Account " + req.getAccountId() + " has status " + account.getStatus() + ".",
                HttpStatus.UNPROCESSABLE_ENTITY);
        }

        String cardNumber = generateCardNumber();
        String masked = maskCardNumber(cardNumber);
        LocalDate expiry = LocalDate.now().plusYears(3);

        Card.CardType type = req.getCardType() != null
                ? Card.CardType.valueOf(req.getCardType().toUpperCase())
                : Card.CardType.VIRTUAL;

        Card card = Card.builder()
                .accountId(req.getAccountId())
                .userId(userId)
                .cardNumber(cardNumber)
                .maskedNumber(masked)
                .cardholderName(req.getCardholderName() != null ? req.getCardholderName() : "Card Holder")
                .cardType(type)
                .status(Card.CardStatus.ACTIVE)
                .expiryMonth((short) expiry.getMonthValue())
                .expiryYear((short) expiry.getYear())
                .spendingLimit(req.getSpendingLimit())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return toResponse(cardRepository.save(card));
    }

    public List<CardResponse> getCardsForUser(UUID userId) {
        return cardRepository.findByUserIdAndStatusNot(userId, Card.CardStatus.CANCELLED)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public CardResponse freezeCard(UUID cardId, UUID userId) {
        Card card = getCardForUser(cardId, userId);
        card.setStatus(Card.CardStatus.FROZEN);
        card.setUpdatedAt(LocalDateTime.now());
        return toResponse(cardRepository.save(card));
    }

    @Transactional
    public CardResponse unfreezeCard(UUID cardId, UUID userId) {
        Card card = getCardForUser(cardId, userId);
        card.setStatus(Card.CardStatus.ACTIVE);
        card.setUpdatedAt(LocalDateTime.now());
        return toResponse(cardRepository.save(card));
    }

    @Transactional
    public CardResponse updateLimit(UUID cardId, UUID userId, java.math.BigDecimal limit) {
        Card card = getCardForUser(cardId, userId);
        card.setSpendingLimit(limit);
        card.setUpdatedAt(LocalDateTime.now());
        return toResponse(cardRepository.save(card));
    }

    @Transactional
    public void cancelCard(UUID cardId, UUID userId) {
        Card card = getCardForUser(cardId, userId);
        card.setStatus(Card.CardStatus.CANCELLED);
        card.setUpdatedAt(LocalDateTime.now());
        cardRepository.save(card);
    }

    private Card getCardForUser(UUID cardId, UUID userId) {
        return cardRepository.findById(cardId)
                .filter(c -> c.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Card not found"));
    }

    private String generateCardNumber() {
        // Generate a 16-digit Luhn-valid card number with 4370 prefix (test range)
        StringBuilder sb = new StringBuilder("4370");
        for (int i = 0; i < 11; i++) sb.append(secureRandom.nextInt(10));
        String partial = sb.toString();
        int checkDigit = luhnCheckDigit(partial);
        return partial + checkDigit;
    }

    private String maskCardNumber(String number) {
        return "**** **** **** " + number.substring(number.length() - 4);
    }

    private int luhnCheckDigit(String partial) {
        int sum = 0;
        boolean alternate = true;
        for (int i = partial.length() - 1; i >= 0; i--) {
            int n = partial.charAt(i) - '0';
            if (alternate) { n *= 2; if (n > 9) n -= 9; }
            sum += n;
            alternate = !alternate;
        }
        return (10 - (sum % 10)) % 10;
    }

    private CardResponse toResponse(Card card) {
        return CardResponse.builder()
                .id(card.getId())
                .accountId(card.getAccountId())
                .maskedNumber(card.getMaskedNumber())
                .cardholderName(card.getCardholderName())
                .cardType(card.getCardType().name())
                .status(card.getStatus().name())
                .expiryMonth(card.getExpiryMonth())
                .expiryYear(card.getExpiryYear())
                .spendingLimit(card.getSpendingLimit())
                .createdAt(card.getCreatedAt())
                .build();
    }
}
