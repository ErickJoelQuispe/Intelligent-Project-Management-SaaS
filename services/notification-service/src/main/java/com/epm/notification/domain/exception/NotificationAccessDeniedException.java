package com.epm.notification.domain.exception;

import java.util.UUID;

/**
 * Thrown when a user attempts to access a notification that belongs to another user.
 */
public class NotificationAccessDeniedException extends RuntimeException {

    public NotificationAccessDeniedException(UUID notificationId, UUID userId) {
        super("Access denied: notification " + notificationId + " does not belong to user " + userId);
    }
}
