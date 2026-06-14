-- Remove COMPLETED from the projects.status CHECK constraint (FIX 10).
-- COMPLETED was dead code — no use case ever set this status. No existing rows
-- can have status = 'COMPLETED', so this migration is safe to apply without a
-- data backfill step.
--
-- The existing constraint name is inferred from the V001 DDL inline CHECK.
-- PostgreSQL names inline CHECK constraints as <table>_<column>_check.
-- We drop and recreate with only the two valid values.

ALTER TABLE projects
    DROP CONSTRAINT IF EXISTS projects_status_check;

ALTER TABLE projects
    ADD CONSTRAINT projects_status_check
        CHECK (status IN ('ACTIVE', 'ARCHIVED'));
