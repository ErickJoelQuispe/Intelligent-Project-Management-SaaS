package com.epm.notification.domain.port.in;

import java.util.UUID;

import com.epm.notification.domain.model.Notification;
import com.epm.notification.domain.model.NotificationType;

/**
 * Input port: create a new notification from a task-domain event.
 */
public interface CreateNotificationUseCase {

    /**
     * Creates and persists a new notification.
     *
     * @param tenantId        the tenant ID
     * @param recipientUserId the user to notify
     * @param type            the notification type
     * @param referenceId     the task ID that triggered the notification
     * @param message         human-readable message
     * @return the persisted notification
     */
    Notification create(UUID tenantId, UUID recipientUserId, NotificationType type,
            UUID referenceId, String message);
}
