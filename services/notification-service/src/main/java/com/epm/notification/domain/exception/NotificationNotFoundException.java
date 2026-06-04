package com.epm.notification.domain.exception;

import java.util.UUID;

/**
 * Thrown when a notification is not found by ID.
 */
public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(UUID notificationId) {
        super("Notification not found: " + notificationId);
    }
}
