package com.epm.notification.application.usecase;

import java.util.List;
import java.util.UUID;

import com.epm.notification.domain.exception.NotificationAccessDeniedException;
import com.epm.notification.domain.exception.NotificationNotFoundException;
import com.epm.notification.domain.model.Notification;
import com.epm.notification.domain.model.NotificationType;
import com.epm.notification.domain.port.in.CountUnreadUseCase;
import com.epm.notification.domain.port.in.CreateNotificationUseCase;
import com.epm.notification.domain.port.in.ListNotificationsUseCase;
import com.epm.notification.domain.port.in.MarkAllReadUseCase;
import com.epm.notification.domain.port.in.MarkAsReadUseCase;
import com.epm.notification.domain.port.out.NotificationRepository;

/**
 * Application service implementing all notification use cases.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class NotificationApplicationService
        implements CreateNotificationUseCase, ListNotificationsUseCase,
                   MarkAsReadUseCase, MarkAllReadUseCase, CountUnreadUseCase {

    private final NotificationRepository notificationRepository;

    public NotificationApplicationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    // ── CreateNotificationUseCase ─────────────────────────────────────────

    @Override
    public Notification create(UUID tenantId, UUID recipientUserId, NotificationType type,
            UUID referenceId, String message) {
        Notification notification = Notification.create(tenantId, recipientUserId, type, referenceId, message);
        return notificationRepository.save(notification);
    }

    // ── ListNotificationsUseCase ──────────────────────────────────────────

    @Override
    public List<Notification> listForUser(UUID tenantId, UUID recipientUserId) {
        return notificationRepository.findByTenantIdAndRecipientUserId(tenantId, recipientUserId);
    }

    // ── MarkAsReadUseCase ─────────────────────────────────────────────────

    @Override
    public void markAsRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));

        if (!notification.getRecipientUserId().equals(userId)) {
            throw new NotificationAccessDeniedException(notificationId, userId);
        }

        notification.markAsRead();
        notificationRepository.save(notification);
    }

    // ── MarkAllReadUseCase ────────────────────────────────────────────────

    @Override
    public void markAllAsRead(UUID tenantId, UUID recipientUserId) {
        notificationRepository.markAllAsRead(tenantId, recipientUserId);
    }

    // ── CountUnreadUseCase ────────────────────────────────────────────────

    @Override
    public int countUnread(UUID tenantId, UUID recipientUserId) {
        return notificationRepository.countUnread(tenantId, recipientUserId);
    }
}
