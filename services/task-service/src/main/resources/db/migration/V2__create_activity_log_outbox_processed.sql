-- Activity log table
CREATE TABLE activity_log (
    id         UUID         NOT NULL,
    task_id    UUID         NOT NULL REFERENCES tasks(id),
    tenant_id  UUID         NOT NULL,
    action     VARCHAR(50)  NOT NULL,
    actor_id   UUID         NOT NULL,
    detail     TEXT         NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_activity_log PRIMARY KEY (id)
);

CREATE INDEX idx_activity_log_task ON activity_log (task_id);
CREATE INDEX idx_activity_log_tenant ON activity_log (tenant_id);

-- Outbox events table
CREATE TABLE outbox_events (
    id             UUID         NOT NULL,
    aggregate_id   UUID         NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    topic          VARCHAR(255) NOT NULL,
    payload        TEXT         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ  NULL,
    failed_at      TIMESTAMPTZ  NULL,
    error          TEXT         NULL,
    CONSTRAINT pk_outbox_events PRIMARY KEY (id)
);

CREATE INDEX idx_outbox_relay ON outbox_events (published_at, failed_at) WHERE published_at IS NULL;

-- Processed events table (idempotency for Kafka consumers)
CREATE TABLE processed_events (
    event_id     VARCHAR(255) NOT NULL,
    topic        VARCHAR(255) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_processed_events PRIMARY KEY (event_id)
);
