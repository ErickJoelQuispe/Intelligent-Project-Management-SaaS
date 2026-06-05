package com.epm.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.epm.notification.application.usecase.NotificationApplicationService;
import com.epm.notification.domain.exception.NotificationAccessDeniedException;
import com.epm.notification.domain.exception.NotificationNotFoundException;
import com.epm.notification.domain.model.Notification;
import com.epm.notification.domain.model.NotificationType;
import com.epm.notification.domain.model.UserEmailCache;
import com.epm.notification.domain.model.NotificationChannel;
import com.epm.notification.domain.model.NotificationPreference;
import com.epm.notification.domain.port.out.EmailPort;
import com.epm.notification.domain.port.out.NotificationPreferenceRepository;
import com.epm.notification.domain.port.out.NotificationRepository;
import com.epm.notification.domain.port.out.UserEmailCacheRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

/**
 * Unit tests for NotificationApplicationService (T-C-03).
 *
 * <p>All ports mocked with Mockito — no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class NotificationApplicationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private EmailPort emailPort;

    @Mock
    private UserEmailCacheRepository userEmailCacheRepository;

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    private NotificationApplicationService service;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new NotificationApplicationService(
                notificationRepository, emailPort, userEmailCacheRepository,
                preferenceRepository, new SimpleMeterRegistry());
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
        when(userEmailCacheRepository.findByUserId(userId)).thenReturn(Optional.empty());

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
        when(userEmailCacheRepository.findByUserId(userId)).thenReturn(Optional.empty());

        Notification result = service.create(tenantId, userId, NotificationType.TASK_STATUS_CHANGED,
                referenceId, "Status changed");

        assertThat(result.getType()).isEqualTo(NotificationType.TASK_STATUS_CHANGED);
    }

    // ── Preference check: IN_APP disabled → notification not persisted ─────

    @Test
    void create_whenInAppPreferenceDisabled_skipsNotificationPersist() {
        UUID referenceId = UUID.randomUUID();
        NotificationPreference disabledPref = new NotificationPreference(
                UUID.randomUUID(), userId, tenantId,
                NotificationType.TASK_ASSIGNED, NotificationChannel.IN_APP, false);
        when(preferenceRepository.findByUserIdAndEventTypeAndChannel(
                userId, NotificationType.TASK_ASSIGNED, NotificationChannel.IN_APP))
                .thenReturn(Optional.of(disabledPref));

        service.create(tenantId, userId, NotificationType.TASK_ASSIGNED, referenceId, "Task assigned");

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void create_whenInAppPreferenceEnabled_persistsNotification() {
        UUID referenceId = UUID.randomUUID();
        NotificationPreference enabledPref = new NotificationPreference(
                UUID.randomUUID(), userId, tenantId,
                NotificationType.TASK_ASSIGNED, NotificationChannel.IN_APP, true);
        when(preferenceRepository.findByUserIdAndEventTypeAndChannel(
                userId, NotificationType.TASK_ASSIGNED, NotificationChannel.IN_APP))
                .thenReturn(Optional.of(enabledPref));
        Notification expected = Notification.create(tenantId, userId,
                NotificationType.TASK_ASSIGNED, referenceId, "Task assigned");
        when(notificationRepository.save(any())).thenReturn(expected);
        when(userEmailCacheRepository.findByUserId(userId)).thenReturn(Optional.empty());

        Notification result = service.create(tenantId, userId, NotificationType.TASK_ASSIGNED,
                referenceId, "Task assigned");

        assertThat(result).isNotNull();
        verify(notificationRepository).save(any(Notification.class));
    }

    // ── Preference check: EMAIL disabled → email not dispatched ───────────

    @Test
    void create_whenEmailPreferenceDisabled_skipsEmailDispatch() {
        UUID referenceId = UUID.randomUUID();
        // IN_APP enabled (default)
        when(preferenceRepository.findByUserIdAndEventTypeAndChannel(
                userId, NotificationType.TASK_ASSIGNED, NotificationChannel.IN_APP))
                .thenReturn(Optional.empty()); // default → enabled
        // EMAIL disabled
        NotificationPreference emailDisabled = new NotificationPreference(
                UUID.randomUUID(), userId, tenantId,
                NotificationType.TASK_ASSIGNED, NotificationChannel.EMAIL, false);
        when(preferenceRepository.findByUserIdAndEventTypeAndChannel(
                userId, NotificationType.TASK_ASSIGNED, NotificationChannel.EMAIL))
                .thenReturn(Optional.of(emailDisabled));
        Notification expected = Notification.create(tenantId, userId,
                NotificationType.TASK_ASSIGNED, referenceId, "Task assigned");
        when(notificationRepository.save(any())).thenReturn(expected);

        service.create(tenantId, userId, NotificationType.TASK_ASSIGNED, referenceId, "Task assigned");

        verify(emailPort, never()).send(any(), any(), any(), any());
    }

    // ── Email dispatch: cache hit → send ──────────────────────────────────

    @Test
    void create_whenEmailCacheHit_sendsEmail() {
        UUID referenceId = UUID.randomUUID();
        Notification expected = Notification.create(tenantId, userId,
                NotificationType.TASK_ASSIGNED, referenceId, "Task assigned to you");
        when(notificationRepository.save(any())).thenReturn(expected);
        UserEmailCache cachedEmail = new UserEmailCache(userId, tenantId, "user@example.com", LocalDateTime.now());
        when(userEmailCacheRepository.findByUserId(userId)).thenReturn(Optional.of(cachedEmail));

        service.create(tenantId, userId, NotificationType.TASK_ASSIGNED, referenceId, "Task assigned to you");

        verify(emailPort).send(
                org.mockito.ArgumentMatchers.eq("user@example.com"),
                any(String.class),
                org.mockito.ArgumentMatchers.eq("email/task-assigned-v1"),
                any(Map.class));
    }

    @Test
    void create_whenEmailCacheMiss_skipsEmailAndDoesNotFail() {
        UUID referenceId = UUID.randomUUID();
        Notification expected = Notification.create(tenantId, userId,
                NotificationType.TASK_ASSIGNED, referenceId, "Task assigned to you");
        when(notificationRepository.save(any())).thenReturn(expected);
        when(userEmailCacheRepository.findByUserId(userId)).thenReturn(Optional.empty());

        // Should not throw
        service.create(tenantId, userId, NotificationType.TASK_ASSIGNED, referenceId, "Task assigned to you");

        verify(emailPort, never()).send(any(), any(), any(), any());
    }

    @Test
    void create_whenEmailPortThrows_notificationIsStillPersisted() {
        UUID referenceId = UUID.randomUUID();
        Notification expected = Notification.create(tenantId, userId,
                NotificationType.TASK_ASSIGNED, referenceId, "Task assigned to you");
        when(notificationRepository.save(any())).thenReturn(expected);
        UserEmailCache cachedEmail = new UserEmailCache(userId, tenantId, "user@example.com", LocalDateTime.now());
        when(userEmailCacheRepository.findByUserId(userId)).thenReturn(Optional.of(cachedEmail));
        org.mockito.Mockito.doThrow(new RuntimeException("SMTP down"))
                .when(emailPort).send(any(), any(), any(), any());

        // Should not throw despite email failure
        Notification result = service.create(tenantId, userId, NotificationType.TASK_ASSIGNED,
                referenceId, "Task assigned to you");

        // Notification was persisted
        assertThat(result).isNotNull();
        verify(notificationRepository).save(any(Notification.class));
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
