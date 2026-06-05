-- V3: add account-lockout columns to the users table.
-- failed_login_attempts — incremented on each BadCredentialsException; reset on success.
-- locked_until          — when non-null the account is locked until this timestamp.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS failed_login_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS locked_until          TIMESTAMP;
