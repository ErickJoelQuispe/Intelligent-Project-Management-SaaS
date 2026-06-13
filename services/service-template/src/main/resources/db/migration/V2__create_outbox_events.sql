-- V2: Create outbox_events table for the transactional outbox pattern.
-- Events are inserted in the same transaction as the aggregate (atomic).
-- OutboxRelayExecutor reads pending/failed rows and forwards them to Kafka.

CREATE TABLE IF NOT EXISTS outbox_events (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id   UUID         NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    topic          VARCHAR(255) NOT NULL,
    -- payload is deliberately TEXT, not JSONB: the relay treats it as an opaque string
    -- (read once, forwarded to Kafka verbatim) and never queries inside it, so JSONB's
    -- indexing/operator support would add cost with no benefit here.
    payload        TEXT         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ  NULL,
    failed_at      TIMESTAMPTZ  NULL,
    error          TEXT         NULL
);

-- Partial index for the relay's pending-batch query (published_at IS NULL AND failed_at IS NULL).
-- Only unpublished, non-failed rows are indexed — keeps the index small and query fast.
CREATE INDEX idx_outbox_unpublished
    ON outbox_events (created_at)
    WHERE published_at IS NULL
      AND failed_at IS NULL;

-- Partial index for the relay's retry-batch query (published_at IS NULL AND failed_at IS NOT NULL).
-- Ordered by failed_at so the oldest failures are retried first.
CREATE INDEX idx_outbox_failed
    ON outbox_events (failed_at, created_at)
    WHERE published_at IS NULL
      AND failed_at IS NOT NULL;
