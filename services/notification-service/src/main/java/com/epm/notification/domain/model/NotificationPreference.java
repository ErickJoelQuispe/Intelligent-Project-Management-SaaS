package com.epm.notification.domain.model;

import java.util.UUID;

/**
 * Domain model representing a user's per-channel, per-event-type notification preference.
 *
 * <p>Pure Java — no Spring or JPA annotations.
 * Models an opt-out configuration: if no row exists for a given (userId, eventType, channel)
 * tuple, the default is {@code enabled=true}.
 */
public class NotificationPreference {

    private final UUID id;
    private final UUID userId;
    private final UUID tenantId;
    private final NotificationType eventType;
    private final NotificationChannel channel;
    private final boolean enabled;

    public NotificationPreference(UUID id, UUID userId, UUID tenantId,
            NotificationType eventType, NotificationChannel channel, boolean enabled) {
        this.id = id;
        this.userId = userId;
        this.tenantId = tenantId;
        this.eventType = eventType;
        this.channel = channel;
        this.enabled = enabled;
    }

    /**
     * Factory for creating a new preference (generates a new UUID for id).
     */
    public static NotificationPreference create(UUID userId, UUID tenantId,
            NotificationType eventType, NotificationChannel channel, boolean enabled) {
        return new NotificationPreference(UUID.randomUUID(), userId, tenantId, eventType, channel, enabled);
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getTenantId() { return tenantId; }
    public NotificationType getEventType() { return eventType; }
    public NotificationChannel getChannel() { return channel; }
    public boolean isEnabled() { return enabled; }
}
