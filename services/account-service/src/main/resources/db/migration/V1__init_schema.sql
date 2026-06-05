-- account-service initial schema

CREATE TABLE accounts (
    account_id  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID           NOT NULL,
    iban        VARCHAR(34)    NOT NULL UNIQUE,
    type        VARCHAR(50)    NOT NULL,
    balance     NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    currency    VARCHAR(3)     NOT NULL DEFAULT 'EUR',
    status      VARCHAR(255)   NOT NULL DEFAULT 'PENDING_APPROVAL',
    created_at  TIMESTAMP      NOT NULL
);

CREATE INDEX idx_accounts_user_id ON accounts(user_id);
CREATE INDEX idx_accounts_status  ON accounts(status);

CREATE TABLE verification_documents (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id            UUID,
    user_id               UUID         NOT NULL,
    selfie_base64         TEXT         NOT NULL,
    selfie_content_type   VARCHAR(100) NOT NULL,
    id_card_base64        TEXT         NOT NULL,
    id_card_content_type  VARCHAR(100) NOT NULL,
    submitted_at          TIMESTAMP    NOT NULL
);

CREATE UNIQUE INDEX idx_verification_documents_user_id ON verification_documents(user_id);

CREATE TABLE beneficiaries (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL,
    name        VARCHAR(100) NOT NULL,
    iban        VARCHAR(34)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    CONSTRAINT uq_beneficiary_user_iban UNIQUE (user_id, iban)
);

CREATE INDEX idx_beneficiaries_user_id ON beneficiaries(user_id);

CREATE TABLE processed_saga_events (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id  UUID        NOT NULL,
    event_type      VARCHAR(20) NOT NULL,
    processed_at    TIMESTAMP   NOT NULL,
    CONSTRAINT uq_processed_saga_tx_type UNIQUE (transaction_id, event_type)
);
