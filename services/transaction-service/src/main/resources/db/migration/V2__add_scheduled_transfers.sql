-- Scheduled/recurring transfers
CREATE TABLE IF NOT EXISTS scheduled_transfers (
    id                   UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    from_account_id      UUID           NOT NULL,
    to_account_id        UUID           NOT NULL,
    initiated_by_user_id UUID           NOT NULL,
    amount               NUMERIC(15, 2) NOT NULL,
    currency             VARCHAR(3)     NOT NULL DEFAULT 'EUR',
    description          TEXT,
    frequency            VARCHAR(20)    NOT NULL,   -- WEEKLY | MONTHLY
    next_execution_date  DATE           NOT NULL,
    active               BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMP      NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_scheduled_transfers_user   ON scheduled_transfers(initiated_by_user_id);
CREATE INDEX IF NOT EXISTS idx_scheduled_transfers_active ON scheduled_transfers(active, next_execution_date);
