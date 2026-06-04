package com.epm.notification.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.epm.notification.domain.model.NotificationChannel;
import com.epm.notification.domain.model.NotificationPreference;
import com.epm.notification.domain.model.NotificationType;

/**
 * Out port for persisting and querying notification preferences.
 *
 * <p>Pure Java interface — no Spring annotations.
 * Implemented by infrastructure adapters.
 */
public interface NotificationPreferenceRepository {

    /**
     * Find a preference for a specific user, event type, and channel.
     *
     * @return empty if no explicit preference has been saved (default: enabled)
     */
    Optional<NotificationPreference> findByUserIdAndEventTypeAndChannel(
            UUID userId, NotificationType eventType, NotificationChannel channel);

    /**
     * Upsert a preference: insert if new, update if the (userId, eventType, channel) row exists.
     */
    void upsert(NotificationPreference preference);

    /**
     * Return all preferences saved for the given user.
     */
    List<NotificationPreference> findAllByUserId(UUID userId);
}
