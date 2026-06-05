-- transaction-service initial schema

CREATE TABLE transactions (
    id                    UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    from_account_id       UUID           NOT NULL,
    to_account_id         UUID           NOT NULL,
    initiated_by_user_id  UUID           NOT NULL,
    amount                NUMERIC(15, 2) NOT NULL,
    currency              VARCHAR(3)     NOT NULL DEFAULT 'EUR',
    type                  VARCHAR(50)    NOT NULL DEFAULT 'TRANSFER',
    status                VARCHAR(50)    NOT NULL DEFAULT 'PENDING',
    description           TEXT,
    idempotency_key       VARCHAR(255)   UNIQUE,
    created_at            TIMESTAMP      NOT NULL,
    completed_at          TIMESTAMP
);

CREATE INDEX idx_transactions_from_account ON transactions(from_account_id);
CREATE INDEX idx_transactions_to_account   ON transactions(to_account_id);
CREATE INDEX idx_transactions_status       ON transactions(status);
CREATE INDEX idx_transactions_created_at   ON transactions(created_at DESC);
