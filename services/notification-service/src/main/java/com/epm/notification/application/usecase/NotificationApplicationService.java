package com.epm.notification.application.usecase;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.epm.notification.domain.exception.NotificationAccessDeniedException;
import com.epm.notification.domain.exception.NotificationNotFoundException;
import com.epm.notification.domain.model.Notification;
import com.epm.notification.domain.model.NotificationChannel;
import com.epm.notification.domain.model.NotificationPreference;
import com.epm.notification.domain.model.NotificationType;
import com.epm.notification.domain.model.UserEmailCache;
import com.epm.notification.domain.port.in.CountUnreadUseCase;
import com.epm.notification.domain.port.in.CreateNotificationUseCase;
import com.epm.notification.domain.port.in.ListNotificationsUseCase;
import com.epm.notification.domain.port.in.MarkAllReadUseCase;
import com.epm.notification.domain.port.in.MarkAsReadUseCase;
import com.epm.notification.domain.port.out.EmailPort;
import com.epm.notification.domain.port.out.NotificationPreferenceRepository;
import com.epm.notification.domain.port.out.NotificationRepository;
import com.epm.notification.domain.port.out.UserEmailCacheRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application service implementing all notification use cases.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 *
 * <p>After persisting a notification, attempts email dispatch via {@link EmailPort}
 * using the email resolved from {@link UserEmailCacheRepository}.
 * Email failure MUST NOT roll back notification persistence.
 */
public class NotificationApplicationService
        implements CreateNotificationUseCase, ListNotificationsUseCase,
                   MarkAsReadUseCase, MarkAllReadUseCase, CountUnreadUseCase {

    private static final Logger log = LoggerFactory.getLogger(NotificationApplicationService.class);

    private final NotificationRepository notificationRepository;
    private final EmailPort emailPort;
    private final UserEmailCacheRepository userEmailCacheRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final MeterRegistry meterRegistry;

    public NotificationApplicationService(
            NotificationRepository notificationRepository,
            EmailPort emailPort,
            UserEmailCacheRepository userEmailCacheRepository,
            NotificationPreferenceRepository preferenceRepository,
            MeterRegistry meterRegistry) {
        this.notificationRepository = notificationRepository;
        this.emailPort = emailPort;
        this.userEmailCacheRepository = userEmailCacheRepository;
        this.preferenceRepository = preferenceRepository;
        this.meterRegistry = meterRegistry;
    }

    // ── CreateNotificationUseCase ─────────────────────────────────────────

    @Override
    public Notification create(UUID tenantId, UUID recipientUserId, NotificationType type,
            UUID referenceId, String message) {
        // Check IN_APP preference — default enabled=true if no row exists
        Optional<NotificationPreference> inAppPref = preferenceRepository
                .findByUserIdAndEventTypeAndChannel(recipientUserId, type, NotificationChannel.IN_APP);
        boolean inAppEnabled = inAppPref.map(NotificationPreference::isEnabled).orElse(true);

        if (!inAppEnabled) {
            log.debug("IN_APP notification suppressed by user preference: userId={}, type={}",
                    recipientUserId, type);
            // Return a transient notification (not persisted) for downstream callers
            return Notification.create(tenantId, recipientUserId, type, referenceId, message);
        }

        Notification notification = Notification.create(tenantId, recipientUserId, type, referenceId, message);
        Notification saved = notificationRepository.save(notification);

        Counter.builder("notifications.sent")
                .tag("type", type.name())
                .register(meterRegistry)
                .increment();

        // Best-effort email dispatch — failures MUST NOT propagate
        dispatchEmail(saved);

        return saved;
    }

    // ── ListNotificationsUseCase ──────────────────────────────────────────

    @Override
    public List<Notification> listForUser(UUID tenantId, UUID recipientUserId) {
        return notificationRepository.findByTenantIdAndRecipientUserId(tenantId, recipientUserId);
    }

    @Override
    public List<Notification> listForUserPaged(UUID tenantId, UUID recipientUserId, int page, int size) {
        return notificationRepository.findByTenantIdAndRecipientUserIdPaged(tenantId, recipientUserId, page, size);
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

    // ── Email dispatch (fire-and-forget) ──────────────────────────────────

    private void dispatchEmail(Notification notification) {
        try {
            // Check EMAIL preference — default enabled=true if no row exists
            Optional<NotificationPreference> emailPref = preferenceRepository
                    .findByUserIdAndEventTypeAndChannel(
                            notification.getRecipientUserId(), notification.getType(),
                            NotificationChannel.EMAIL);
            boolean emailEnabled = emailPref.map(NotificationPreference::isEnabled).orElse(true);

            if (!emailEnabled) {
                log.debug("EMAIL notification suppressed by user preference: userId={}, type={}",
                        notification.getRecipientUserId(), notification.getType());
                return;
            }

            Optional<UserEmailCache> cached =
                    userEmailCacheRepository.findByUserId(notification.getRecipientUserId());

            if (cached.isEmpty()) {
                log.warn("Email cache miss for userId={} — skipping email for notificationType={}",
                        notification.getRecipientUserId(), notification.getType());
                return;
            }

            String toEmail = cached.get().getEmail();
            String templateName = resolveTemplateName(notification.getType());
            String subject = resolveSubject(notification.getType());
            Map<String, Object> variables = Map.of(
                    "message", notification.getMessage(),
                    "notificationType", notification.getType().name());

            emailPort.send(toEmail, subject, templateName, variables);

        } catch (Exception e) {
            log.error("Email dispatch failed for notificationId={}: {}",
                    notification.getId(), e.getMessage(), e);
            // Best-effort — do NOT rethrow
        }
    }

    private String resolveTemplateName(NotificationType type) {
        return switch (type) {
            case TASK_ASSIGNED -> "email/task-assigned-v1";
            case TASK_CREATED -> "email/task-created-v1";
            case MEMBER_JOINED_TEAM -> "email/member-joined-v1";
            case PROJECT_CREATED -> "email/project-created-v1";
            default -> "email/task-created-v1"; // safe fallback
        };
    }

    private String resolveSubject(NotificationType type) {
        return switch (type) {
            case TASK_ASSIGNED -> "Task Assigned to You";
            case TASK_CREATED -> "New Task Created";
            case TASK_STATUS_CHANGED -> "Task Status Changed";
            case TASK_DELETED -> "Task Deleted";
            case MEMBER_JOINED_TEAM -> "You Joined a Team";
            case MEMBER_LEFT_TEAM -> "You Left a Team";
            case PROJECT_CREATED -> "New Project Created";
            case PROJECT_ARCHIVED -> "Project Archived";
            case TEAM_ASSIGNED_TO_PROJECT -> "Team Assigned to Project";
            case INVITATION_SENT -> "You've been invited to join a workspace";
        };
    }
}
