package com.epm.notification.domain.model;

/**
 * Delivery channels for notifications.
 *
 * <p>Pure Java enum — no Spring or JPA annotations.
 * Used by {@code NotificationPreference} to model per-channel opt-in/opt-out settings.
 */
public enum NotificationChannel {
    IN_APP,
    EMAIL
}
