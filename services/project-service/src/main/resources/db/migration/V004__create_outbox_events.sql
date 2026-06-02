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
