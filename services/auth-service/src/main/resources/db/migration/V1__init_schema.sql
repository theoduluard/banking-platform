-- auth-service initial schema

CREATE TABLE users (
    user_id                          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email                            VARCHAR(255) NOT NULL UNIQUE,
    lastname                         VARCHAR(255) NOT NULL,
    firstname                        VARCHAR(255) NOT NULL,
    role                             VARCHAR(50)  NOT NULL,
    password                         VARCHAR(255) NOT NULL,
    created_at                       DATE         NOT NULL,
    is_active                        BOOLEAN      NOT NULL DEFAULT TRUE,
    email_verified                   BOOLEAN,
    email_verification_token         VARCHAR(255) UNIQUE,
    email_verification_token_expiry  TIMESTAMP,
    password_reset_token             VARCHAR(255) UNIQUE,
    password_reset_token_expiry      TIMESTAMP
);

CREATE TABLE refresh_tokens (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    user_id     UUID         NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    expires_at  TIMESTAMP    NOT NULL,
    created_at  TIMESTAMP    NOT NULL
);

CREATE INDEX idx_refresh_tokens_user_id  ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);
