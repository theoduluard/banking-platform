-- OTP challenges: short-lived one-time codes issued on login (2FA)

CREATE TABLE otp_challenges (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    session_token VARCHAR(255) NOT NULL UNIQUE,
    user_id       UUID         NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    code_hash     VARCHAR(255) NOT NULL,
    attempts      INT          NOT NULL DEFAULT 0,
    expires_at    TIMESTAMP    NOT NULL,
    created_at    TIMESTAMP    NOT NULL
);

CREATE INDEX idx_otp_challenges_session ON otp_challenges(session_token);
CREATE INDEX idx_otp_challenges_expires ON otp_challenges(expires_at);
