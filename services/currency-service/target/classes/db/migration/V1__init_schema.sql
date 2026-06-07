CREATE TABLE IF NOT EXISTS exchange_rates (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    base_currency VARCHAR(3)   NOT NULL,
    target_currency VARCHAR(3) NOT NULL,
    rate          DECIMAL(18,8) NOT NULL,
    updated_at    TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_rates_pair ON exchange_rates(base_currency, target_currency);

-- Seed common rates relative to EUR
INSERT INTO exchange_rates (base_currency, target_currency, rate) VALUES
    ('EUR', 'USD', 1.08000000),
    ('EUR', 'GBP', 0.86000000),
    ('EUR', 'CHF', 0.96000000),
    ('EUR', 'JPY', 162.00000000),
    ('EUR', 'CAD', 1.47000000),
    ('USD', 'EUR', 0.92600000),
    ('GBP', 'EUR', 1.16300000),
    ('CHF', 'EUR', 1.04200000),
    ('JPY', 'EUR', 0.00617000),
    ('CAD', 'EUR', 0.68000000)
ON CONFLICT (base_currency, target_currency) DO NOTHING;
