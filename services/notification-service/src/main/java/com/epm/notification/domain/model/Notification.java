package com.epm.notification.domain.model;

import java.time.Instant;
import java.util.UUID;

import com.github.f4b6a3.uuid.UuidCreator;

/**
 * Notification aggregate root.
 *
 * <p>Represents an in-app notification delivered to a user when a task-domain
 * event occurs. Tenant-isolated by {@code tenantId}.
 *
 * <p>No Spring or JPA annotations — pure Java domain object.
 */
public class Notification {

    private final UUID id;
    private final UUID tenantId;
    private final UUID recipientUserId;
    private final NotificationType type;
    private final UUID referenceId;
    private final String message;
    private boolean read;
    private final Instant createdAt;

    // ── Factory ──────────────────────────────────────────────────────────────

    /**
     * Creates a new unread Notification from event data.
     *
     * @param tenantId        the tenant this notification belongs to
     * @param recipientUserId the user who will receive this notification
     * @param type            the notification type
     * @param referenceId     the task ID that triggered this notification
     * @param message         human-readable notification message
     * @return a new unread Notification
     */
    public static Notification create(UUID tenantId, UUID recipientUserId,
            NotificationType type, UUID referenceId, String message) {
        return new Notification(
                UuidCreator.getTimeOrderedEpoch(),
                tenantId,
                recipientUserId,
                type,
                referenceId,
                message,
                false,
                Instant.now());
    }

    /**
     * Reconstitutes a Notification from persistence (no side effects).
     */
    public static Notification reconstitute(UUID id, UUID tenantId, UUID recipientUserId,
            NotificationType type, UUID referenceId, String message,
            boolean read, Instant createdAt) {
        return new Notification(id, tenantId, recipientUserId, type, referenceId, message, read, createdAt);
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    private Notification(UUID id, UUID tenantId, UUID recipientUserId,
            NotificationType type, UUID referenceId, String message,
            boolean read, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.recipientUserId = recipientUserId;
        this.type = type;
        this.referenceId = referenceId;
        this.message = message;
        this.read = read;
        this.createdAt = createdAt;
    }

    // ── Business methods ─────────────────────────────────────────────────────

    /**
     * Marks this notification as read.
     */
    public void markAsRead() {
        this.read = true;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public UUID getId() { return id; }

    public UUID getTenantId() { return tenantId; }

    public UUID getRecipientUserId() { return recipientUserId; }

    public NotificationType getType() { return type; }

    public UUID getReferenceId() { return referenceId; }

    public String getMessage() { return message; }

    public boolean isRead() { return read; }

    public Instant getCreatedAt() { return createdAt; }
}
