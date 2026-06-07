-- Audit log is append-only: no UPDATE or DELETE should ever run on this table.
CREATE TABLE IF NOT EXISTS audit_events (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type  VARCHAR(100) NOT NULL,
    source      VARCHAR(50)  NOT NULL,
    user_id     UUID,
    entity_type VARCHAR(50),
    entity_id   UUID,
    payload     TEXT,
    ip_address  VARCHAR(45),
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_audit_user_id    ON audit_events(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_event_type ON audit_events(event_type);
CREATE INDEX IF NOT EXISTS idx_audit_created_at ON audit_events(created_at DESC);
