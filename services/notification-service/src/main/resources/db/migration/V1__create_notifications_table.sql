-- V1: Create the notifications table for the notification-service aggregate

CREATE TABLE notifications (
    id                UUID         NOT NULL,
    tenant_id         UUID         NOT NULL,
    recipient_user_id UUID         NOT NULL,
    type              VARCHAR(30)  NOT NULL
                      CHECK (type IN ('TASK_CREATED','TASK_ASSIGNED','TASK_STATUS_CHANGED','TASK_DELETED')),
    reference_id      UUID         NOT NULL,
    message           TEXT         NOT NULL,
    read              BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_notifications PRIMARY KEY (id)
);

-- Index for tenant + user queries (most common access pattern)
CREATE INDEX idx_notifications_tenant_user
    ON notifications (tenant_id, recipient_user_id);

-- Partial index for unread-only queries (improves unread count + listing performance)
CREATE INDEX idx_notifications_unread
    ON notifications (tenant_id, recipient_user_id, read)
    WHERE read = FALSE;
