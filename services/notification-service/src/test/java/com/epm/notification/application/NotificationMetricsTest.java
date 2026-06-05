package com.epm.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.epm.notification.application.usecase.NotificationApplicationService;
import com.epm.notification.domain.model.Notification;
import com.epm.notification.domain.model.NotificationType;
import com.epm.notification.domain.port.out.EmailPort;
import com.epm.notification.domain.port.out.NotificationPreferenceRepository;
import com.epm.notification.domain.port.out.NotificationRepository;
import com.epm.notification.domain.port.out.UserEmailCacheRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests verifying that {@link NotificationApplicationService} increments the
 * {@code notifications.sent} Micrometer counter on each successful notification creation.
 *
 * <p>Uses {@link SimpleMeterRegistry} — no Prometheus or Spring context needed.
 */
@ExtendWith(MockitoExtension.class)
class NotificationMetricsTest {

    @Mock
    NotificationRepository notificationRepository;

    @Mock
    EmailPort emailPort;

    @Mock
    UserEmailCacheRepository userEmailCacheRepository;

    @Mock
    NotificationPreferenceRepository preferenceRepository;

    SimpleMeterRegistry meterRegistry;
    NotificationApplicationService service;

    UUID tenantId;
    UUID userId;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new NotificationApplicationService(
                notificationRepository, emailPort, userEmailCacheRepository,
                preferenceRepository, meterRegistry);
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    void createNotification_incrementsNotificationsSentCounter() {
        UUID referenceId = UUID.randomUUID();
        Notification saved = Notification.create(tenantId, userId,
                NotificationType.TASK_ASSIGNED, referenceId, "Task assigned");
        when(notificationRepository.save(any())).thenReturn(saved);
        when(userEmailCacheRepository.findByUserId(userId)).thenReturn(Optional.empty());

        service.create(tenantId, userId, NotificationType.TASK_ASSIGNED, referenceId, "Task assigned");

        Counter counter = meterRegistry.find("notifications.sent")
                .tag("type", NotificationType.TASK_ASSIGNED.name())
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void createNotification_differentTypes_counterTaggedByType() {
        UUID refId1 = UUID.randomUUID();
        UUID refId2 = UUID.randomUUID();

        Notification saved1 = Notification.create(tenantId, userId,
                NotificationType.TASK_ASSIGNED, refId1, "Task assigned");
        Notification saved2 = Notification.create(tenantId, userId,
                NotificationType.PROJECT_CREATED, refId2, "Project created");

        when(notificationRepository.save(any()))
                .thenReturn(saved1)
                .thenReturn(saved2);
        when(userEmailCacheRepository.findByUserId(userId)).thenReturn(Optional.empty());

        service.create(tenantId, userId, NotificationType.TASK_ASSIGNED, refId1, "Task assigned");
        service.create(tenantId, userId, NotificationType.PROJECT_CREATED, refId2, "Project created");

        Counter taskCounter = meterRegistry.find("notifications.sent")
                .tag("type", NotificationType.TASK_ASSIGNED.name())
                .counter();
        Counter projectCounter = meterRegistry.find("notifications.sent")
                .tag("type", NotificationType.PROJECT_CREATED.name())
                .counter();

        assertThat(taskCounter).isNotNull();
        assertThat(taskCounter.count()).isEqualTo(1.0);
        assertThat(projectCounter).isNotNull();
        assertThat(projectCounter.count()).isEqualTo(1.0);
    }
}
