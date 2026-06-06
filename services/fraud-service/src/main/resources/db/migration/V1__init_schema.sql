CREATE TABLE IF NOT EXISTS fraud_alerts (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id  UUID         NOT NULL,
    user_id         UUID         NOT NULL,
    account_id      UUID         NOT NULL,
    amount          DECIMAL(15,2) NOT NULL,
    rule_triggered  VARCHAR(100) NOT NULL,
    risk_score      SMALLINT     NOT NULL DEFAULT 0,
    status          VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    resolved_by     UUID,
    resolution_note TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    resolved_at     TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_fraud_user_id  ON fraud_alerts(user_id);
CREATE INDEX IF NOT EXISTS idx_fraud_status   ON fraud_alerts(status);
CREATE INDEX IF NOT EXISTS idx_fraud_tx_id    ON fraud_alerts(transaction_id);
