-- V3: Create notification_preferences table and update notifications.type CHECK constraint
--
-- NOTE: V3 runs BEFORE V4 (user_email_cache). No conflict.
-- V3 must handle the full set of NotificationType values added through Phase 6 WU-B + WU-C.

-- 1. Drop the old CHECK constraint on notifications.type (was limited to 4 values from V1)
ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS notifications_type_check;

-- 2. Add updated CHECK constraint including all 9 notification types
ALTER TABLE notifications
    ADD CONSTRAINT notifications_type_check
        CHECK (type IN (
            'TASK_CREATED',
            'TASK_ASSIGNED',
            'TASK_STATUS_CHANGED',
            'TASK_DELETED',
            'MEMBER_JOINED_TEAM',
            'MEMBER_LEFT_TEAM',
            'PROJECT_CREATED',
            'PROJECT_ARCHIVED',
            'TEAM_ASSIGNED_TO_PROJECT'
        ));

-- 3. Create notification_preferences table
CREATE TABLE notification_preferences (
    id         UUID         NOT NULL,
    tenant_id  UUID         NOT NULL,
    user_id    UUID         NOT NULL,
    event_type VARCHAR(64)  NOT NULL,
    channel    VARCHAR(16)  NOT NULL,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_notification_preferences PRIMARY KEY (id),
    CONSTRAINT uq_notif_pref_user_type_channel UNIQUE (user_id, event_type, channel)
);

-- 4. Index for (user_id, event_type, channel) — primary lookup pattern
CREATE INDEX idx_notif_prefs_user
    ON notification_preferences (user_id, event_type, channel);
