-- H3 (poison-event cap) + H1 (SKIP LOCKED relay) support columns.
-- attempts: incremented on every publish attempt (success or failure).
-- parked:   true once an event exceeds MAX_ATTEMPTS; parked rows are never re-selected.
ALTER TABLE outbox_events ADD COLUMN attempts INT     NOT NULL DEFAULT 0;
ALTER TABLE outbox_events ADD COLUMN parked   BOOLEAN NOT NULL DEFAULT FALSE;

-- Keep the relay claim queries (FOR UPDATE SKIP LOCKED) index-backed and
-- exclude parked rows from the pending scan.
DROP INDEX IF EXISTS idx_outbox_unpublished;
CREATE INDEX idx_outbox_unpublished
    ON outbox_events(created_at)
    WHERE published_at IS NULL AND parked = FALSE;
