-- V5: email_change_requests table
--
-- Supports the three-step email-change flow:
--   1. User requests change (currentPassword + newEmail) → OTP sent to current email
--   2. User submits OTP → verify_token sent to new email
--   3. User clicks link → email updated, all sessions revoked
--
-- A user can only have one active request at a time (old requests are deleted
-- when a new one is created).  Expired entries are purged by the nightly job.

CREATE TABLE IF NOT EXISTS email_change_requests (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    new_email        VARCHAR(255) NOT NULL,

    -- Step 1: OTP sent to current email (hash only — never stored in plain text)
    otp_code_hash    VARCHAR(255),

    -- Step 2: OTP validated, verification link sent to new email
    otp_verified_at  TIMESTAMP,
    verify_token     VARCHAR(255) UNIQUE,

    -- Step 3: verification link clicked, email updated
    completed_at     TIMESTAMP,

    expires_at       TIMESTAMP    NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ecr_user_id ON email_change_requests(user_id);
CREATE INDEX IF NOT EXISTS idx_ecr_token   ON email_change_requests(verify_token);
CREATE INDEX IF NOT EXISTS idx_ecr_expires ON email_change_requests(expires_at);
