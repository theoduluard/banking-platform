CREATE TABLE IF NOT EXISTS generated_documents (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL,
    account_id      UUID         NOT NULL,
    document_type   VARCHAR(50)  NOT NULL DEFAULT 'RIB',
    filename        VARCHAR(255) NOT NULL,
    generated_at    TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_docs_user_id    ON generated_documents(user_id);
CREATE INDEX IF NOT EXISTS idx_docs_account_id ON generated_documents(account_id);
