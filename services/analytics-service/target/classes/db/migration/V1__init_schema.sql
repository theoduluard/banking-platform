CREATE TABLE IF NOT EXISTS spending_aggregates (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL,
    account_id      UUID         NOT NULL,
    year            SMALLINT     NOT NULL,
    month           SMALLINT     NOT NULL,
    category        VARCHAR(50)  NOT NULL DEFAULT 'OTHER',
    total_debit     DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_credit    DECIMAL(15,2) NOT NULL DEFAULT 0,
    tx_count        INT          NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_agg_unique ON spending_aggregates(user_id, account_id, year, month, category);
CREATE INDEX IF NOT EXISTS idx_agg_user_id ON spending_aggregates(user_id);
