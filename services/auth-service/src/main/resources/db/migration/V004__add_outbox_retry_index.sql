CREATE INDEX idx_outbox_failed
    ON outbox_events (failed_at, created_at)
    WHERE published_at IS NULL AND failed_at IS NOT NULL;
