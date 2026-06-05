-- V4: defensive catch-up migration.
--
-- WHY THIS EXISTS:
--   In a scenario where V2 (otp_challenges) or V3 (lockout columns) was recorded
--   as SUCCESS in flyway_schema_history but the DDL never actually persisted
--   (e.g. the volume was recreated from a backup predating those migrations),
--   Flyway skips the earlier scripts and Hibernate validation crashes with
--   "Schema validation: missing table [otp_challenges]".
--
--   All statements below are fully idempotent (IF NOT EXISTS / IF EXISTS guard on
--   column add-ons), so this migration is harmless when the schema is already
--   correct, and a full self-heal when it is not.

-- ── otp_challenges (V2 safety-net) ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS otp_challenges (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    session_token VARCHAR(255) NOT NULL UNIQUE,
    user_id       UUID         NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    code_hash     VARCHAR(255) NOT NULL,
    attempts      INT          NOT NULL DEFAULT 0,
    expires_at    TIMESTAMP    NOT NULL,
    created_at    TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_otp_challenges_session ON otp_challenges(session_token);
CREATE INDEX IF NOT EXISTS idx_otp_challenges_expires ON otp_challenges(expires_at);

-- ── lockout columns (V3 safety-net) ─────────────────────────────────────────
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS failed_login_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS locked_until          TIMESTAMP;
