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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionEventConsumerTest {

    @Mock
    private SpendingAggregateRepository repo;

    private TransactionEventConsumer consumer;

    private final UUID USER_ID    = UUID.randomUUID();
    private final UUID ACCOUNT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        consumer = new TransactionEventConsumer(repo);
        // Default: no existing aggregate
        when(repo.findByUserIdAndAccountIdAndYearAndMonthAndCategory(
                any(), any(), anyShort(), anyShort(), anyString()))
                .thenReturn(Optional.empty());
        when(repo.save(any(SpendingAggregate.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private String msg(String type, String extra) {
        return "{\"type\":\"%s\",\"userId\":\"%s\",\"accountId\":\"%s\"%s}"
                .formatted(type, USER_ID, ACCOUNT_ID, extra);
    }

    // ── DEBIT ─────────────────────────────────────────────────────────────────

    @Test
    void consume_debit_shouldSaveNewAggregateWithDebitAmount() {
        consumer.consume(msg("DEBIT", ",\"amount\":\"200.00\""));

        ArgumentCaptor<SpendingAggregate> captor = ArgumentCaptor.forClass(SpendingAggregate.class);
        verify(repo).save(captor.capture());
        SpendingAggregate agg = captor.getValue();

        assertThat(agg.getUserId()).isEqualTo(USER_ID);
        assertThat(agg.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(agg.getTotalDebit()).isEqualByComparingTo("200.00");
        assertThat(agg.getTotalCredit()).isEqualByComparingTo("0");
        assertThat(agg.getTxCount()).isEqualTo(1);
        assertThat(agg.getCategory()).isEqualTo("OTHER");
        assertThat(agg.getYear()).isEqualTo((short) LocalDate.now().getYear());
        assertThat(agg.getMonth()).isEqualTo((short) LocalDate.now().getMonthValue());
    }

    // ── CREDIT ────────────────────────────────────────────────────────────────

    @Test
    void consume_credit_shouldSaveNewAggregateWithCreditAmount() {
        consumer.consume(msg("CREDIT", ",\"amount\":\"500.00\""));

        ArgumentCaptor<SpendingAggregate> captor = ArgumentCaptor.forClass(SpendingAggregate.class);
        verify(repo).save(captor.capture());
        SpendingAggregate agg = captor.getValue();

        assertThat(agg.getTotalCredit()).isEqualByComparingTo("500.00");
        assertThat(agg.getTotalDebit()).isEqualByComparingTo("0");
    }

    // ── TRANSFER_COMPLETED ────────────────────────────────────────────────────

    @Test
    void consume_transferCompleted_countsAsDebit() {
        consumer.consume(msg("TRANSFER_COMPLETED", ",\"amount\":\"1000.00\""));

        ArgumentCaptor<SpendingAggregate> captor = ArgumentCaptor.forClass(SpendingAggregate.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getTotalDebit()).isEqualByComparingTo("1000.00");
    }

    // ── Existing aggregate accumulation ───────────────────────────────────────

    @Test
    void consume_existingAggregate_shouldAccumulateAndIncrementTxCount() {
        SpendingAggregate existing = SpendingAggregate.builder()
                .userId(USER_ID).accountId(ACCOUNT_ID)
                .year((short) LocalDate.now().getYear())
                .month((short) LocalDate.now().getMonthValue())
                .category("OTHER")
                .totalDebit(new BigDecimal("100.00"))
                .totalCredit(BigDecimal.ZERO)
                .txCount(2)
                .updatedAt(java.time.LocalDateTime.now().minusDays(1))
                .build();

        when(repo.findByUserIdAndAccountIdAndYearAndMonthAndCategory(
                eq(USER_ID), eq(ACCOUNT_ID), anyShort(), anyShort(), eq("OTHER")))
                .thenReturn(Optional.of(existing));

        consumer.consume(msg("DEBIT", ",\"amount\":\"50.00\""));

        ArgumentCaptor<SpendingAggregate> captor = ArgumentCaptor.forClass(SpendingAggregate.class);
        verify(repo).save(captor.capture());
        SpendingAggregate saved = captor.getValue();

        assertThat(saved.getTotalDebit()).isEqualByComparingTo("150.00");
        assertThat(saved.getTxCount()).isEqualTo(3);
    }

    // ── Category field ────────────────────────────────────────────────────────

    @Test
    void consume_withExplicitCategory_shouldUseItInLookupAndAggregate() {
        consumer.consume(msg("DEBIT", ",\"amount\":\"99.99\",\"category\":\"GROCERIES\""));

        ArgumentCaptor<SpendingAggregate> captor = ArgumentCaptor.forClass(SpendingAggregate.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo("GROCERIES");

        verify(repo).findByUserIdAndAccountIdAndYearAndMonthAndCategory(
                eq(USER_ID), eq(ACCOUNT_ID), anyShort(), anyShort(), eq("GROCERIES"));
    }

    @Test
    void consume_missingCategory_shouldDefaultToOther() {
        consumer.consume(msg("DEBIT", ",\"amount\":\"10.00\""));

        ArgumentCaptor<SpendingAggregate> captor = ArgumentCaptor.forClass(SpendingAggregate.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo("OTHER");
    }

    // ── Unknown / filtered types ───────────────────────────────────────────────

    @Test
    void consume_unknownType_shouldSkipAndNotSave() {
        consumer.consume(msg("REFUND", ",\"amount\":\"50.00\""));
        verify(repo, never()).save(any());
    }

    @Test
    void consume_loginType_shouldSkipAndNotSave() {
        consumer.consume("{\"type\":\"LOGIN\",\"userId\":\"" + USER_ID + "\"}");
        verify(repo, never()).save(any());
    }

    // ── Error handling ────────────────────────────────────────────────────────

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
}
