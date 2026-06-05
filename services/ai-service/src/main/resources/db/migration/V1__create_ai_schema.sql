-- AI request log — auditing and cost tracking for every LLM call
CREATE TABLE ai_request_log (
    id             UUID           PRIMARY KEY,
    user_id        UUID           NOT NULL,
    tenant_id      UUID           NOT NULL,
    prompt_type    VARCHAR(50)    NOT NULL,
    prompt_text    TEXT           NOT NULL,
    response_text  TEXT,
    input_tokens   INT,
    output_tokens  INT,
    estimated_cost DECIMAL(12,8),
    model          VARCHAR(100),
    duration_ms    BIGINT,
    cached         BOOLEAN        DEFAULT FALSE,
    created_at     TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Outbox events table — transactional outbox pattern for Kafka publishing
CREATE TABLE outbox_event (
    id            UUID           PRIMARY KEY,
    aggregate_id  VARCHAR(255)   NOT NULL,
    event_type    VARCHAR(255)   NOT NULL,
    event_payload JSONB          NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    published_at  TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_ai_request_log_tenant ON ai_request_log(tenant_id, created_at);
CREATE INDEX idx_outbox_unpublished ON outbox_event WHERE published_at IS NULL;
