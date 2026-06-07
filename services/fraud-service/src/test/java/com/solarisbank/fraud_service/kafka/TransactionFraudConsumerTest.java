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

@ExtendWith(MockitoExtension.class)
class TransactionFraudConsumerTest {

    @Mock
    private FraudAlertRepository repo;

    private TransactionFraudConsumer consumer;

    private final UUID TX_ID     = UUID.randomUUID();
    private final UUID USER_ID   = UUID.randomUUID();
    private final UUID ACCOUNT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        consumer = new TransactionFraudConsumer(repo);
    }

    private String buildMessage(String type, String amount) {
        return """
                {"type":"%s","transactionId":"%s","userId":"%s","accountId":"%s","amount":"%s"}
                """.formatted(type, TX_ID, USER_ID, ACCOUNT_ID, amount).strip();
    }

    // ── Rule 1: HIGH_AMOUNT ────────────────────────────────────────────────────

    @Test
    void consume_highAmount_shouldCreateHighAmountAlert() {
        when(repo.save(any(FraudAlert.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.consume(buildMessage("TRANSFER_COMPLETED", "15000"));

        ArgumentCaptor<FraudAlert> captor = ArgumentCaptor.forClass(FraudAlert.class);
        verify(repo).save(captor.capture());
        FraudAlert alert = captor.getValue();

        assertThat(alert.getRuleTriggered()).isEqualTo("HIGH_AMOUNT");
        assertThat(alert.getRiskScore()).isEqualTo((short) 75);
        assertThat(alert.getStatus()).isEqualTo(FraudAlert.AlertStatus.OPEN);
        assertThat(alert.getAmount()).isEqualByComparingTo("15000");
        assertThat(alert.getUserId()).isEqualTo(USER_ID);
    }

    // ── Rule 2: ROUND_AMOUNT ───────────────────────────────────────────────────

    @Test
    void consume_roundAmount_shouldCreateRoundAmountAlert() {
        when(repo.save(any(FraudAlert.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.consume(buildMessage("DEBIT", "6000"));

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

        consumer.consume(buildMessage("DEBIT", "12000"));

        ArgumentCaptor<FraudAlert> captor = ArgumentCaptor.forClass(FraudAlert.class);
        verify(repo).save(captor.capture());
        FraudAlert alert = captor.getValue();

        assertThat(alert.getRuleTriggered()).isEqualTo("HIGH_AMOUNT,ROUND_AMOUNT");
        assertThat(alert.getRiskScore()).isEqualTo((short) 95);
    }

    @Test
    void consume_extremelyHighRoundAmount_riskScoreCappedAt100() {
        when(repo.save(any(FraudAlert.class))).thenAnswer(inv -> inv.getArgument(0));

        // Already 75 for HIGH_AMOUNT + 20 for ROUND_AMOUNT = 95, no overflow beyond 100
        consumer.consume(buildMessage("TRANSFER_COMPLETED", "100000"));

        ArgumentCaptor<FraudAlert> captor = ArgumentCaptor.forClass(FraudAlert.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getRiskScore()).isLessThanOrEqualTo((short) 100);
    }

    // ── No alert ───────────────────────────────────────────────────────────────

    @Test
    void consume_lowAmount_noAlertCreated() {
        consumer.consume(buildMessage("DEBIT", "100"));
        verify(repo, never()).save(any());
    }

    @Test
    void consume_amount5000Exactly_noRoundAmountRule() {
        // Rule requires amount > 5000, not >= 5000
        consumer.consume(buildMessage("DEBIT", "5000"));
        verify(repo, never()).save(any());
    }

    // ── Ignored event types ────────────────────────────────────────────────────

    @Test
    void consume_creditType_shouldBeSkipped() {
        consumer.consume(buildMessage("CREDIT", "50000"));
        verify(repo, never()).save(any());
    }

    @Test
    void consume_unknownType_shouldBeSkipped() {
        consumer.consume(buildMessage("UNKNOWN", "99999"));
        verify(repo, never()).save(any());
    }

    // ── Error handling ─────────────────────────────────────────────────────────

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
}
