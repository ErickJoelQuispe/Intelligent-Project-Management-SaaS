package com.epm.notification.infrastructure.adapter.in.web;

import com.epm.notification.domain.model.NotificationChannel;
import com.epm.notification.domain.model.NotificationPreference;
import com.epm.notification.domain.model.NotificationType;

/**
 * DTO for serializing a notification preference to the REST API.
 */
public class PreferenceResponse {

    private final NotificationType eventType;
    private final NotificationChannel channel;
    private final boolean enabled;

    public PreferenceResponse(NotificationType eventType, NotificationChannel channel, boolean enabled) {
        this.eventType = eventType;
        this.channel = channel;
        this.enabled = enabled;
    }

    public static PreferenceResponse from(NotificationPreference pref) {
        return new PreferenceResponse(pref.getEventType(), pref.getChannel(), pref.isEnabled());
    }

    public NotificationType getEventType() { return eventType; }
    public NotificationChannel getChannel() { return channel; }
    public boolean isEnabled() { return enabled; }
}
