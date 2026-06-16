-- V5: Scope idempotency per (event_id, topic).
--
-- V2 created processed_events with PRIMARY KEY (event_id) only, even though a `topic`
-- column exists. The same envelope eventId can legitimately arrive on two DIFFERENT source
-- topics (project.events, task.events, user.events) — distinct domain events that collide on
-- the generated id. With a single-column PK, the second topic's
-- INSERT ... ON CONFLICT (event_id) DO NOTHING returns 0 rows and the event is SILENTLY DROPPED.
--
-- This migration makes idempotency scoped per (event_id, topic): drop the old single-column PK
-- and add a composite PK on (event_id, topic). The constraint name is reused (pk_processed_events).
-- topic is already NOT NULL (V2), so it is a valid PK column.

ALTER TABLE processed_events DROP CONSTRAINT pk_processed_events;

ALTER TABLE processed_events
    ADD CONSTRAINT pk_processed_events PRIMARY KEY (event_id, topic);
