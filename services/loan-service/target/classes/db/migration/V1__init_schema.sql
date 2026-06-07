CREATE TABLE IF NOT EXISTS loans (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID          NOT NULL,
    account_id          UUID          NOT NULL,
    amount              DECIMAL(15,2) NOT NULL,
    interest_rate       DECIMAL(5,2)  NOT NULL,
    duration_months     INT           NOT NULL,
    monthly_payment     DECIMAL(15,2) NOT NULL,
    total_repayment     DECIMAL(15,2) NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    purpose             VARCHAR(255),
    admin_note          TEXT,
    disbursed_at        TIMESTAMP,
    created_at          TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_loans_user_id ON loans(user_id);
CREATE INDEX IF NOT EXISTS idx_loans_status  ON loans(status);
