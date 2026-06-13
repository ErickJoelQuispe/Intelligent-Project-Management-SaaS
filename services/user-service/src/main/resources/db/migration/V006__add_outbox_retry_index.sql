-- Partial index to accelerate the outbox retry query:
-- SELECT ... WHERE published_at IS NULL AND failed_at < :threshold ORDER BY created_at ASC
-- The WHERE clause mirrors the relay retry condition so Postgres can use an
-- index scan + heap fetch for the retry predicate (a SELECT * / native query
-- reads non-indexed columns, so an index-only scan is not possible).
CREATE INDEX idx_outbox_failed
    ON outbox_events (failed_at, created_at)
    WHERE published_at IS NULL
      AND failed_at IS NOT NULL;
