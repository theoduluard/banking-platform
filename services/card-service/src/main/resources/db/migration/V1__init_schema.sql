CREATE TABLE IF NOT EXISTS cards (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id      UUID         NOT NULL,
    user_id         UUID         NOT NULL,
    card_number     VARCHAR(19)  NOT NULL UNIQUE,
    masked_number   VARCHAR(19)  NOT NULL,
    cardholder_name VARCHAR(255) NOT NULL,
    card_type       VARCHAR(20)  NOT NULL DEFAULT 'VIRTUAL', -- VIRTUAL, PHYSICAL
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, FROZEN, CANCELLED
    expiry_month    SMALLINT     NOT NULL,
    expiry_year     SMALLINT     NOT NULL,
    cvv_hash        VARCHAR(255),
    spending_limit  DECIMAL(15,2),
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_cards_account_id ON cards(account_id);
CREATE INDEX IF NOT EXISTS idx_cards_user_id    ON cards(user_id);
