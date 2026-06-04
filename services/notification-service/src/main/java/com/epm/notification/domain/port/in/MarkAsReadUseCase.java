package com.epm.notification.domain.port.in;

import java.util.UUID;

/**
 * Input port: mark a single notification as read.
 */
public interface MarkAsReadUseCase {

    /**
     * Marks the notification with the given ID as read.
     * Enforces ownership: only the notification's recipient may mark it read.
     *
     * @param notificationId the notification ID
     * @param userId         the authenticated user attempting the operation
     * @throws com.epm.notification.domain.exception.NotificationNotFoundException  if not found
     * @throws com.epm.notification.domain.exception.NotificationAccessDeniedException if user is not the recipient
     */
    void markAsRead(UUID notificationId, UUID userId);
}
