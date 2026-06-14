-- Partial index for the retry relay query:
-- WHERE published_at IS NULL AND failed_at IS NOT NULL ORDER BY created_at ASC
-- Supports lockRetryBatch() efficiently without a sequential scan.
CREATE INDEX idx_outbox_failed
    ON outbox_events (failed_at, created_at)
    WHERE published_at IS NULL
      AND failed_at IS NOT NULL;
