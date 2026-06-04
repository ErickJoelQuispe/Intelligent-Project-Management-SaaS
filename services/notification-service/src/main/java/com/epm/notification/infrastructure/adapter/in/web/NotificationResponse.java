package com.epm.notification.infrastructure.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

import com.epm.notification.domain.model.Notification;
import com.epm.notification.domain.model.NotificationType;

/**
 * REST response DTO for a Notification.
 */
public record NotificationResponse(
        UUID id,
        NotificationType type,
        UUID referenceId,
        String message,
        boolean read,
        Instant createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getReferenceId(),
                notification.getMessage(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
