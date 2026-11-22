CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    rule_code VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    network VARCHAR(32) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    heading_path VARCHAR(512) NOT NULL,
    content TEXT NOT NULL,
    content_sha256 VARCHAR(64) NOT NULL,
    embedding_csv TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_knowledge_tenant_rule UNIQUE (tenant_id, rule_code)
);

CREATE INDEX IF NOT EXISTS idx_knowledge_tenant_reason
    ON knowledge_chunk (tenant_id, reason_code);

CREATE TABLE IF NOT EXISTS outbox_event (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    topic VARCHAR(128) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE
);
