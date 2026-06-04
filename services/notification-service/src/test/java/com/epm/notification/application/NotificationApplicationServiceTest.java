package com.epm.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.epm.notification.application.usecase.NotificationApplicationService;
import com.epm.notification.domain.exception.NotificationAccessDeniedException;
import com.epm.notification.domain.exception.NotificationNotFoundException;
import com.epm.notification.domain.model.Notification;
import com.epm.notification.domain.model.NotificationType;
import com.epm.notification.domain.port.out.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for NotificationApplicationService (T-C-03).
 *
 * <p>All ports mocked with Mockito — no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class NotificationApplicationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationApplicationService service;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new NotificationApplicationService(notificationRepository);
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    // ── T-C-03: CreateNotificationUseCase ────────────────────────────────

    @Test
    void create_persistsAndReturnsNotification() {
        UUID referenceId = UUID.randomUUID();
        Notification expected = Notification.create(tenantId, userId,
                NotificationType.TASK_ASSIGNED, referenceId, "Task assigned to you");
        when(notificationRepository.save(any())).thenReturn(expected);

        Notification result = service.create(tenantId, userId, NotificationType.TASK_ASSIGNED,
                referenceId, "Task assigned to you");

        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(NotificationType.TASK_ASSIGNED);
        assertThat(result.getRecipientUserId()).isEqualTo(userId);
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void create_withDifferentType_createsNotificationWithCorrectType() {
        UUID referenceId = UUID.randomUUID();
        Notification expected = Notification.create(tenantId, userId,
                NotificationType.TASK_STATUS_CHANGED, referenceId, "Status changed");
        when(notificationRepository.save(any())).thenReturn(expected);

        Notification result = service.create(tenantId, userId, NotificationType.TASK_STATUS_CHANGED,
                referenceId, "Status changed");

        assertThat(result.getType()).isEqualTo(NotificationType.TASK_STATUS_CHANGED);
    }

    // ── T-C-03: ListNotificationsUseCase ─────────────────────────────────

    @Test
    void listForUser_returnsNotificationsForUser() {
        List<Notification> expected = List.of(
                Notification.create(tenantId, userId, NotificationType.TASK_CREATED,
                        UUID.randomUUID(), "Task created"),
                Notification.create(tenantId, userId, NotificationType.TASK_ASSIGNED,
                        UUID.randomUUID(), "Task assigned"));
        when(notificationRepository.findByTenantIdAndRecipientUserId(tenantId, userId))
                .thenReturn(expected);

        List<Notification> result = service.listForUser(tenantId, userId);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(n -> n.getRecipientUserId().equals(userId));
    }

    @Test
    void listForUser_whenNoNotifications_returnsEmptyList() {
        when(notificationRepository.findByTenantIdAndRecipientUserId(tenantId, userId))
                .thenReturn(List.of());

        List<Notification> result = service.listForUser(tenantId, userId);

        assertThat(result).isEmpty();
    }

    // ── T-C-03: MarkAsReadUseCase ─────────────────────────────────────────

    @Test
    void markAsRead_whenOwner_marksNotificationAsRead() {
        UUID notifId = UUID.randomUUID();
        Notification notification = Notification.reconstitute(
                notifId, tenantId, userId, NotificationType.TASK_ASSIGNED,
                UUID.randomUUID(), "Task assigned", false,
                java.time.Instant.now());
        when(notificationRepository.findById(notifId)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any())).thenReturn(notification);

        service.markAsRead(notifId, userId);

        assertThat(notification.isRead()).isTrue();
        verify(notificationRepository).save(notification);
    }

    @Test
    void markAsRead_whenNotFound_throwsNotificationNotFoundException() {
        UUID notifId = UUID.randomUUID();
        when(notificationRepository.findById(notifId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(notifId, userId))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    @Test
    void markAsRead_whenNotOwner_throwsNotificationAccessDeniedException() {
        UUID notifId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Notification notification = Notification.reconstitute(
                notifId, tenantId, otherUserId, NotificationType.TASK_ASSIGNED,
                UUID.randomUUID(), "Task assigned to other user", false,
                java.time.Instant.now());
        when(notificationRepository.findById(notifId)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> service.markAsRead(notifId, userId))
                .isInstanceOf(NotificationAccessDeniedException.class);
    }

    // ── T-C-03: MarkAllReadUseCase ────────────────────────────────────────

    @Test
    void markAllAsRead_delegatesToRepository() {
        service.markAllAsRead(tenantId, userId);

        verify(notificationRepository).markAllAsRead(tenantId, userId);
    }

    // ── T-C-03: CountUnreadUseCase ────────────────────────────────────────

    @Test
    void countUnread_returnsCountFromRepository() {
        when(notificationRepository.countUnread(tenantId, userId)).thenReturn(5);

        int result = service.countUnread(tenantId, userId);

        assertThat(result).isEqualTo(5);
    }

    @Test
    void countUnread_whenNoUnread_returnsZero() {
        when(notificationRepository.countUnread(tenantId, userId)).thenReturn(0);

        int result = service.countUnread(tenantId, userId);

        assertThat(result).isZero();
    }
}
