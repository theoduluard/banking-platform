package com.solarisbank.card_service.service;

import com.solarisbank.card_service.client.AccountClient;
import com.solarisbank.card_service.client.AccountResponse;
import com.solarisbank.card_service.dto.CardResponse;
import com.solarisbank.card_service.dto.CreateCardRequest;
import com.solarisbank.card_service.exception.BusinessException;
import com.solarisbank.card_service.model.Card;
import com.solarisbank.card_service.repository.CardRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private AccountClient accountClient;

    @InjectMocks
    private CardService cardService;

    private UUID userId;
    private UUID cardId;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        userId    = UUID.randomUUID();
        cardId    = UUID.randomUUID();
        accountId = UUID.randomUUID();
    }

    /** Helper — returns an ACTIVE CHECKING account by default. */
    private AccountResponse checkingAccount() {
        AccountResponse acc = new AccountResponse();
        acc.setType("CHECKING");
        acc.setStatus("ACTIVE");
        return acc;
    }

    private Card buildCard(Card.CardStatus status) {
        return Card.builder()
                .id(cardId)
                .userId(userId)
                .accountId(accountId)
                .cardNumber("4370123456789012")
                .maskedNumber("**** **** **** 9012")
                .cardholderName("John Doe")
                .cardType(Card.CardType.VIRTUAL)
                .status(status)
                .expiryMonth((short) 6)
                .expiryYear((short) 2029)
                .spendingLimit(new BigDecimal("1000.00"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ── createCard ─────────────────────────────────────────────────────────────

    @Test
    void createCard_withVirtualType_shouldSaveAndReturnResponse() {
        CreateCardRequest req = new CreateCardRequest();
        req.setAccountId(accountId);
        req.setCardType("VIRTUAL");
        req.setCardholderName("Jane Doe");
        req.setSpendingLimit(new BigDecimal("500.00"));

        when(accountClient.getAccount(eq(accountId), eq(userId))).thenReturn(checkingAccount());
        Card saved = buildCard(Card.CardStatus.ACTIVE);
        saved.setCardType(Card.CardType.VIRTUAL);
        when(cardRepository.save(any(Card.class))).thenReturn(saved);

        CardResponse resp = cardService.createCard(userId, req);

        ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
        verify(cardRepository).save(captor.capture());
        Card captured = captor.getValue();

        assertThat(captured.getCardType()).isEqualTo(Card.CardType.VIRTUAL);
        assertThat(captured.getStatus()).isEqualTo(Card.CardStatus.ACTIVE);
        assertThat(captured.getUserId()).isEqualTo(userId);
        assertThat(captured.getCardholderName()).isEqualTo("Jane Doe");
        assertThat(captured.getMaskedNumber()).matches("\\*{4} \\*{4} \\*{4} \\d{4}");
        assertThat(resp).isNotNull();
    }

    @Test
    void createCard_withPhysicalType_shouldSetPhysicalType() {
        CreateCardRequest req = new CreateCardRequest();
        req.setAccountId(accountId);
        req.setCardType("PHYSICAL");
        req.setCardholderName("Alice");

        when(accountClient.getAccount(eq(accountId), eq(userId))).thenReturn(checkingAccount());
        Card saved = buildCard(Card.CardStatus.ACTIVE);
        saved.setCardType(Card.CardType.PHYSICAL);
        when(cardRepository.save(any(Card.class))).thenReturn(saved);

        cardService.createCard(userId, req);

        ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
        verify(cardRepository).save(captor.capture());
        assertThat(captor.getValue().getCardType()).isEqualTo(Card.CardType.PHYSICAL);
    }

    @Test
    void createCard_withNullCardType_defaultsToVirtual() {
        CreateCardRequest req = new CreateCardRequest();
        req.setAccountId(accountId);
        req.setCardType(null);

        when(accountClient.getAccount(eq(accountId), eq(userId))).thenReturn(checkingAccount());
        Card saved = buildCard(Card.CardStatus.ACTIVE);
        when(cardRepository.save(any(Card.class))).thenReturn(saved);

        cardService.createCard(userId, req);

        ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
        verify(cardRepository).save(captor.capture());
        assertThat(captor.getValue().getCardType()).isEqualTo(Card.CardType.VIRTUAL);
    }

    @Test
    void createCard_withNullCardholderName_defaultsToCardHolder() {
        CreateCardRequest req = new CreateCardRequest();
        req.setAccountId(accountId);
        req.setCardholderName(null);

        when(accountClient.getAccount(eq(accountId), eq(userId))).thenReturn(checkingAccount());
        Card saved = buildCard(Card.CardStatus.ACTIVE);
        when(cardRepository.save(any(Card.class))).thenReturn(saved);

        cardService.createCard(userId, req);

        ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
        verify(cardRepository).save(captor.capture());
        assertThat(captor.getValue().getCardholderName()).isEqualTo("Card Holder");
    }

    @Test
    void createCard_expirySetThreeYearsInFuture() {
        CreateCardRequest req = new CreateCardRequest();
        req.setAccountId(accountId);

        when(accountClient.getAccount(eq(accountId), eq(userId))).thenReturn(checkingAccount());
        Card saved = buildCard(Card.CardStatus.ACTIVE);
        when(cardRepository.save(any(Card.class))).thenReturn(saved);

        cardService.createCard(userId, req);

        ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
        verify(cardRepository).save(captor.capture());
        int expectedYear = java.time.LocalDate.now().plusYears(3).getYear();
        assertThat(captor.getValue().getExpiryYear()).isEqualTo((short) expectedYear);
    }

    // ── createCard — account type validation ──────────────────────────────────

    @Test
    void createCard_savingsAccount_shouldThrowUnprocessableEntity() {
        CreateCardRequest req = new CreateCardRequest();
        req.setAccountId(accountId);

        AccountResponse savings = new AccountResponse();
        savings.setType("SAVINGS");
        savings.setStatus("ACTIVE");
        when(accountClient.getAccount(eq(accountId), eq(userId))).thenReturn(savings);

        assertThatThrownBy(() -> cardService.createCard(userId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CHECKING");

        verify(cardRepository, never()).save(any());
    }

    @Test
    void createCard_inactiveAccount_shouldThrowUnprocessableEntity() {
        CreateCardRequest req = new CreateCardRequest();
        req.setAccountId(accountId);

        AccountResponse blocked = new AccountResponse();
        blocked.setType("CHECKING");
        blocked.setStatus("BLOCKED");
        when(accountClient.getAccount(eq(accountId), eq(userId))).thenReturn(blocked);

        assertThatThrownBy(() -> cardService.createCard(userId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ACTIVE");

        verify(cardRepository, never()).save(any());
    }

    // ── getCardsForUser ────────────────────────────────────────────────────────

    @Test
    void getCardsForUser_shouldReturnNonCancelledCards() {
        Card card = buildCard(Card.CardStatus.ACTIVE);
        when(cardRepository.findByUserIdAndStatusNot(userId, Card.CardStatus.CANCELLED))
                .thenReturn(List.of(card));

        List<CardResponse> result = cardService.getCardsForUser(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void getCardsForUser_emptyResult_returnsEmptyList() {
        when(cardRepository.findByUserIdAndStatusNot(userId, Card.CardStatus.CANCELLED))
                .thenReturn(List.of());

        assertThat(cardService.getCardsForUser(userId)).isEmpty();
    }

    // ── freezeCard ─────────────────────────────────────────────────────────────

    @Test
    void freezeCard_shouldSetStatusToFrozen() {
        Card card = buildCard(Card.CardStatus.ACTIVE);
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(cardRepository.save(card)).thenReturn(card);

        CardResponse resp = cardService.freezeCard(cardId, userId);

        assertThat(card.getStatus()).isEqualTo(Card.CardStatus.FROZEN);
        assertThat(resp.getStatus()).isEqualTo("FROZEN");
    }

    @Test
    void freezeCard_wrongUser_shouldThrow() {
        Card card = buildCard(Card.CardStatus.ACTIVE);
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> cardService.freezeCard(cardId, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Card not found");
    }

    // ── unfreezeCard ───────────────────────────────────────────────────────────

    @Test
    void unfreezeCard_shouldSetStatusToActive() {
        Card card = buildCard(Card.CardStatus.FROZEN);
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(cardRepository.save(card)).thenReturn(card);

        CardResponse resp = cardService.unfreezeCard(cardId, userId);

        assertThat(card.getStatus()).isEqualTo(Card.CardStatus.ACTIVE);
        assertThat(resp.getStatus()).isEqualTo("ACTIVE");
    }

    // ── updateLimit ────────────────────────────────────────────────────────────

    @Test
    void updateLimit_shouldUpdateSpendingLimit() {
        Card card = buildCard(Card.CardStatus.ACTIVE);
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(cardRepository.save(card)).thenReturn(card);

        BigDecimal newLimit = new BigDecimal("2000.00");
        cardService.updateLimit(cardId, userId, newLimit);

        assertThat(card.getSpendingLimit()).isEqualByComparingTo(newLimit);
    }

    // ── cancelCard ─────────────────────────────────────────────────────────────

    @Test
    void cancelCard_shouldSetStatusToCancelled() {
        Card card = buildCard(Card.CardStatus.ACTIVE);
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(cardRepository.save(card)).thenReturn(card);

        cardService.cancelCard(cardId, userId);

        assertThat(card.getStatus()).isEqualTo(Card.CardStatus.CANCELLED);
    }

    @Test
    void cancelCard_notFound_shouldThrow() {
        when(cardRepository.findById(cardId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.cancelCard(cardId, userId))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
