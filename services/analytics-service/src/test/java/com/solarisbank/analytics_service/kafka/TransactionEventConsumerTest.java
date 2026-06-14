package com.solarisbank.analytics_service.kafka;

import com.solarisbank.analytics_service.model.SpendingAggregate;
import com.solarisbank.analytics_service.repository.SpendingAggregateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for TransactionEventConsumer — consumes "transaction.completed" events
 * published by transaction-service after the debit-credit saga completes.
 *
 * Event shape:
 * {
 *   "transactionId":   "...",
 *   "fromAccountId":   "...",   // debited account
 *   "toAccountId":     "...",   // credited account
 *   "senderUserId":    "...",   // user who initiated
 *   "recipientUserId": "...",   // owner of destination account
 *   "amount":          "100.00",
 *   "currency":        "EUR",
 *   "completedAt":     "2026-06-15T10:00:00"
 * }
 */
@ExtendWith(MockitoExtension.class)
class TransactionEventConsumerTest {

    @Mock
    private SpendingAggregateRepository repo;

    private TransactionEventConsumer consumer;

    private final UUID SENDER_USER_ID    = UUID.randomUUID();
    private final UUID RECIPIENT_USER_ID = UUID.randomUUID();
    private final UUID FROM_ACCOUNT_ID   = UUID.randomUUID();
    private final UUID TO_ACCOUNT_ID     = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        consumer = new TransactionEventConsumer(repo);
        lenient().when(repo.findByUserIdAndAccountIdAndYearAndMonthAndCategory(
                any(), any(), anyShort(), anyShort(), anyString()))
                .thenReturn(Optional.empty());
        lenient().when(repo.save(any(SpendingAggregate.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** Builds a valid transaction.completed JSON event. */
    private String event(String amount) {
        return """
                {
                  "transactionId":   "%s",
                  "fromAccountId":   "%s",
                  "toAccountId":     "%s",
                  "senderUserId":    "%s",
                  "recipientUserId": "%s",
                  "amount":          "%s",
                  "currency":        "EUR",
                  "completedAt":     "2026-06-%02dT10:00:00"
                }
                """.formatted(
                UUID.randomUUID(), FROM_ACCOUNT_ID, TO_ACCOUNT_ID,
                SENDER_USER_ID, RECIPIENT_USER_ID,
                amount, LocalDate.now().getDayOfMonth());
    }

    // ── Debit entry ───────────────────────────────────────────────────────────

    @Test
    void consume_shouldSaveDebitEntryForSender() {
        consumer.consume(event("200.00"));

        ArgumentCaptor<SpendingAggregate> captor = ArgumentCaptor.forClass(SpendingAggregate.class);
        verify(repo, atLeastOnce()).save(captor.capture());

        SpendingAggregate debitAgg = captor.getAllValues().stream()
                .filter(a -> a.getUserId().equals(SENDER_USER_ID))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No debit aggregate saved for sender"));

        assertThat(debitAgg.getAccountId()).isEqualTo(FROM_ACCOUNT_ID);
        assertThat(debitAgg.getTotalDebit()).isEqualByComparingTo("200.00");
        assertThat(debitAgg.getTotalCredit()).isEqualByComparingTo("0");
        assertThat(debitAgg.getTxCount()).isEqualTo(1);
        assertThat(debitAgg.getCategory()).isEqualTo("Virement");
        assertThat(debitAgg.getYear()).isEqualTo((short) LocalDate.now().getYear());
        assertThat(debitAgg.getMonth()).isEqualTo((short) LocalDate.now().getMonthValue());
    }

    // ── Credit entry ──────────────────────────────────────────────────────────

    @Test
    void consume_shouldSaveCreditEntryForRecipient() {
        consumer.consume(event("500.00"));

        ArgumentCaptor<SpendingAggregate> captor = ArgumentCaptor.forClass(SpendingAggregate.class);
        verify(repo, atLeastOnce()).save(captor.capture());

        SpendingAggregate creditAgg = captor.getAllValues().stream()
                .filter(a -> a.getUserId().equals(RECIPIENT_USER_ID))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No credit aggregate saved for recipient"));

        assertThat(creditAgg.getAccountId()).isEqualTo(TO_ACCOUNT_ID);
        assertThat(creditAgg.getTotalCredit()).isEqualByComparingTo("500.00");
        assertThat(creditAgg.getTotalDebit()).isEqualByComparingTo("0");
        assertThat(creditAgg.getTxCount()).isEqualTo(1);
        assertThat(creditAgg.getCategory()).isEqualTo("Virement");
    }

    // ── Two saves per event ───────────────────────────────────────────────────

    @Test
    void consume_shouldSaveTwoAggregatesPerEvent() {
        consumer.consume(event("100.00"));

        // One debit save + one credit save
        verify(repo, times(2)).save(any(SpendingAggregate.class));
    }

    // ── Accumulation on existing aggregate ────────────────────────────────────

    @Test
    void consume_existingDebitAggregate_shouldAccumulateAndIncrementTxCount() {
        SpendingAggregate existing = SpendingAggregate.builder()
                .userId(SENDER_USER_ID).accountId(FROM_ACCOUNT_ID)
                .year((short) LocalDate.now().getYear())
                .month((short) LocalDate.now().getMonthValue())
                .category("Virement")
                .totalDebit(new BigDecimal("100.00"))
                .totalCredit(BigDecimal.ZERO)
                .txCount(2)
                .updatedAt(java.time.LocalDateTime.now().minusDays(1))
                .build();

        when(repo.findByUserIdAndAccountIdAndYearAndMonthAndCategory(
                eq(SENDER_USER_ID), eq(FROM_ACCOUNT_ID), anyShort(), anyShort(), eq("Virement")))
                .thenReturn(Optional.of(existing));

        consumer.consume(event("50.00"));

        ArgumentCaptor<SpendingAggregate> captor = ArgumentCaptor.forClass(SpendingAggregate.class);
        verify(repo, atLeastOnce()).save(captor.capture());

        SpendingAggregate saved = captor.getAllValues().stream()
                .filter(a -> a.getUserId().equals(SENDER_USER_ID))
                .findFirst().orElseThrow();

        assertThat(saved.getTotalDebit()).isEqualByComparingTo("150.00");
        assertThat(saved.getTxCount()).isEqualTo(3);
    }

    // ── Missing required fields ───────────────────────────────────────────────

    @Test
    void consume_missingSenderUserId_shouldSkipAndNotSave() {
        consumer.consume("""
                {"fromAccountId":"%s","toAccountId":"%s","amount":"50.00"}
                """.formatted(FROM_ACCOUNT_ID, TO_ACCOUNT_ID));

        verify(repo, never()).save(any());
    }

    @Test
    void consume_missingFromAccountId_shouldSkipAndNotSave() {
        consumer.consume("""
                {"senderUserId":"%s","toAccountId":"%s","amount":"50.00"}
                """.formatted(SENDER_USER_ID, TO_ACCOUNT_ID));

        verify(repo, never()).save(any());
    }

    // ── Error resilience ──────────────────────────────────────────────────────

    @Test
    void consume_invalidJson_shouldNotThrowAndNotSave() {
        consumer.consume("{invalid json}");
        verify(repo, never()).save(any());
    }

    @Test
    void consume_emptyMessage_shouldNotThrowAndNotSave() {
        consumer.consume("");
        verify(repo, never()).save(any());
    }

    @Test
    void consume_noRecipient_shouldOnlySaveDebitForSender() {
        // Event without recipientUserId/toAccountId — only the sender entry should be saved
        String msgNoRecipient = """
                {
                  "transactionId": "%s",
                  "fromAccountId": "%s",
                  "senderUserId":  "%s",
                  "amount":        "75.00"
                }
                """.formatted(UUID.randomUUID(), FROM_ACCOUNT_ID, SENDER_USER_ID);

        consumer.consume(msgNoRecipient);

        ArgumentCaptor<SpendingAggregate> captor = ArgumentCaptor.forClass(SpendingAggregate.class);
        verify(repo, times(1)).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(SENDER_USER_ID);
        assertThat(captor.getValue().getTotalDebit()).isEqualByComparingTo("75.00");
    }

    // ── Category ──────────────────────────────────────────────────────────────

    @Test
    void consume_categoryIsAlwaysVirement() {
        consumer.consume(event("99.99"));

        ArgumentCaptor<SpendingAggregate> captor = ArgumentCaptor.forClass(SpendingAggregate.class);
        verify(repo, atLeastOnce()).save(captor.capture());

        List<SpendingAggregate> all = captor.getAllValues();
        assertThat(all).allSatisfy(a -> assertThat(a.getCategory()).isEqualTo("Virement"));
    }
}
