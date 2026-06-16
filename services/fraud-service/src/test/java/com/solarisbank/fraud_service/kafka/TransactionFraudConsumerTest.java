package com.solarisbank.fraud_service.kafka;

import com.solarisbank.fraud_service.model.FraudAlert;
import com.solarisbank.fraud_service.repository.FraudAlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TransactionFraudConsumer}.
 *
 * Events follow the {@code TransactionCompletedEvent} shape published on
 * the {@code transaction.completed} Kafka topic:
 * <pre>
 * {
 *   "transactionId":  "&lt;uuid&gt;",
 *   "senderUserId":   "&lt;uuid&gt;",
 *   "fromAccountId":  "&lt;uuid&gt;",
 *   "amount":         "&lt;decimal&gt;"
 * }
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
class TransactionFraudConsumerTest {

    @Mock
    private FraudAlertRepository repo;

    private TransactionFraudConsumer consumer;

    private final UUID TX_ID      = UUID.randomUUID();
    private final UUID USER_ID    = UUID.randomUUID();
    private final UUID ACCOUNT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        consumer = new TransactionFraudConsumer(repo);
    }

    /** Builds a valid TransactionCompletedEvent JSON with the given amount. */
    private String event(String amount) {
        return """
                {"transactionId":"%s","senderUserId":"%s","fromAccountId":"%s","amount":"%s"}
                """.formatted(TX_ID, USER_ID, ACCOUNT_ID, amount).strip();
    }

    // ── Rule 1: HIGH_AMOUNT ────────────────────────────────────────────────────

    @Test
    void consume_highAmount_shouldCreateHighAmountAlert() {
        when(repo.save(any(FraudAlert.class))).thenAnswer(inv -> inv.getArgument(0));

        // 15001 triggers HIGH_AMOUNT (>10000) but is NOT a round number → only one rule fires
        consumer.consume(event("15001"));

        ArgumentCaptor<FraudAlert> captor = ArgumentCaptor.forClass(FraudAlert.class);
        verify(repo).save(captor.capture());
        FraudAlert alert = captor.getValue();

        assertThat(alert.getRuleTriggered()).isEqualTo("HIGH_AMOUNT");
        assertThat(alert.getRiskScore()).isEqualTo((short) 75);
        assertThat(alert.getStatus()).isEqualTo(FraudAlert.AlertStatus.OPEN);
        assertThat(alert.getAmount()).isEqualByComparingTo("15001");
        assertThat(alert.getUserId()).isEqualTo(USER_ID);
        assertThat(alert.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(alert.getTransactionId()).isEqualTo(TX_ID);
    }

    // ── Rule 2: ROUND_AMOUNT ───────────────────────────────────────────────────

    @Test
    void consume_roundAmount_shouldCreateRoundAmountAlert() {
        when(repo.save(any(FraudAlert.class))).thenAnswer(inv -> inv.getArgument(0));

        // 6000 > 5000 and divisible by 1000 → ROUND_AMOUNT
        consumer.consume(event("6000"));

        ArgumentCaptor<FraudAlert> captor = ArgumentCaptor.forClass(FraudAlert.class);
        verify(repo).save(captor.capture());
        FraudAlert alert = captor.getValue();

        assertThat(alert.getRuleTriggered()).isEqualTo("ROUND_AMOUNT");
        assertThat(alert.getRiskScore()).isEqualTo((short) 20);
    }

    // ── Both rules ─────────────────────────────────────────────────────────────

    @Test
    void consume_bothRules_shouldCombineRulesAndCapScore() {
        when(repo.save(any(FraudAlert.class))).thenAnswer(inv -> inv.getArgument(0));

        // 12000 > 10000 AND > 5000 and divisible by 1000 → HIGH_AMOUNT + ROUND_AMOUNT
        consumer.consume(event("12000"));

        ArgumentCaptor<FraudAlert> captor = ArgumentCaptor.forClass(FraudAlert.class);
        verify(repo).save(captor.capture());
        FraudAlert alert = captor.getValue();

        assertThat(alert.getRuleTriggered()).isEqualTo("HIGH_AMOUNT,ROUND_AMOUNT");
        assertThat(alert.getRiskScore()).isEqualTo((short) 95);
    }

    @Test
    void consume_extremelyHighRoundAmount_riskScoreCappedAt100() {
        when(repo.save(any(FraudAlert.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.consume(event("100000"));

        ArgumentCaptor<FraudAlert> captor = ArgumentCaptor.forClass(FraudAlert.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getRiskScore()).isLessThanOrEqualTo((short) 100);
    }

    // ── No alert ───────────────────────────────────────────────────────────────

    @Test
    void consume_lowAmount_noAlertCreated() {
        consumer.consume(event("100"));
        verify(repo, never()).save(any());
    }

    @Test
    void consume_amount5000Exactly_noRoundAmountRule() {
        // Rule requires amount > 5000, not >= 5000
        consumer.consume(event("5000"));
        verify(repo, never()).save(any());
    }

    @Test
    void consume_amount1001NonRound_noAlertCreated() {
        // > 1000 but not divisible by 1000 — doesn't trigger ROUND_AMOUNT
        consumer.consume(event("1001"));
        verify(repo, never()).save(any());
    }

    // ── Missing / malformed fields ─────────────────────────────────────────────

    @Test
    void consume_missingSenderUserId_shouldNotSaveAndNotThrow() {
        // Missing senderUserId → required field absent → skip silently
        String msg = """
                {"transactionId":"%s","fromAccountId":"%s","amount":"50000"}
                """.formatted(TX_ID, ACCOUNT_ID).strip();
        consumer.consume(msg);
        verify(repo, never()).save(any());
    }

    @Test
    void consume_missingAmount_shouldNotSaveAndNotThrow() {
        String msg = """
                {"transactionId":"%s","senderUserId":"%s","fromAccountId":"%s"}
                """.formatted(TX_ID, USER_ID, ACCOUNT_ID).strip();
        consumer.consume(msg);
        verify(repo, never()).save(any());
    }

    // ── Error resilience ───────────────────────────────────────────────────────

    @Test
    void consume_invalidJson_shouldNotThrow() {
        consumer.consume("{not valid json}");
        verify(repo, never()).save(any());
    }

    @Test
    void consume_emptyMessage_shouldNotThrow() {
        consumer.consume("");
        verify(repo, never()).save(any());
    }

    @Test
    void consume_nullMessage_shouldNotThrow() {
        consumer.consume(null);
        verify(repo, never()).save(any());
    }
}
