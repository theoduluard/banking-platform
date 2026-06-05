-- messaging-service initial schema

CREATE TABLE messages (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID         NOT NULL,
    subject                 VARCHAR(200) NOT NULL,
    body                    TEXT         NOT NULL,
    type                    VARCHAR(20)  NOT NULL,
    is_read                 BOOLEAN      NOT NULL DEFAULT FALSE,
    attachment_base64       TEXT,
    attachment_content_type VARCHAR(30),
    attachment_filename     VARCHAR(200),
    created_at              TIMESTAMP    NOT NULL
);

CREATE INDEX idx_messages_user_id    ON messages(user_id);
CREATE INDEX idx_messages_is_read    ON messages(user_id, is_read);
CREATE INDEX idx_messages_created_at ON messages(created_at DESC);

CREATE TABLE support_requests (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID         NOT NULL,
    type                    VARCHAR(30)  NOT NULL,
    subject                 VARCHAR(200) NOT NULL,
    body                    TEXT         NOT NULL,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    attachment_base64       TEXT,
    attachment_content_type VARCHAR(30),
    attachment_filename     VARCHAR(200),
    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP    NOT NULL
);

CREATE INDEX idx_support_requests_user_id ON support_requests(user_id);
CREATE INDEX idx_support_requests_status  ON support_requests(status);

CREATE TABLE support_request_replies (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id              UUID        NOT NULL REFERENCES support_requests(id) ON DELETE CASCADE,
    author_type             VARCHAR(10) NOT NULL,
    author_id               UUID        NOT NULL,
    body                    TEXT        NOT NULL,
    attachment_base64       TEXT,
    attachment_content_type VARCHAR(30),
    attachment_filename     VARCHAR(200),
    created_at              TIMESTAMP   NOT NULL
);

CREATE INDEX idx_support_replies_request_id ON support_request_replies(request_id);
