-- Partial index to speed up the retry batch query:
--   SELECT ... FROM outbox_events WHERE published_at IS NULL AND failed_at < :threshold
-- A SELECT on this index is safe even while FOR UPDATE SKIP LOCKED holds row-level locks.
CREATE INDEX idx_outbox_failed ON outbox_events (failed_at, created_at)
    WHERE published_at IS NULL AND failed_at IS NOT NULL;
