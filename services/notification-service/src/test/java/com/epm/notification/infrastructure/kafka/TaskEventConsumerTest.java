package com.epm.notification.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.epm.notification.application.usecase.NotificationApplicationService;
import com.epm.notification.domain.model.Notification;
import com.epm.notification.domain.model.NotificationType;
import com.epm.notification.domain.port.out.NotificationPushPort;
import com.epm.notification.infrastructure.adapter.in.messaging.TaskEventConsumer;
import com.epm.notification.infrastructure.adapter.out.persistence.ProcessedEventJpaEntity;
import com.epm.notification.infrastructure.adapter.out.persistence.ProcessedEventJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for TaskEventConsumer (T-C-05).
 *
 * <p>Tests event routing logic with mocked dependencies — no Kafka infrastructure needed.
 */
@ExtendWith(MockitoExtension.class)
class TaskEventConsumerTest {

    @Mock
    private NotificationApplicationService notificationService;

    @Mock
    private ProcessedEventJpaRepository processedEventRepo;

    @Mock
    private NotificationPushPort notificationPushPort;

    private TaskEventConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        consumer = new TaskEventConsumer(notificationService, processedEventRepo, notificationPushPort, objectMapper);
    }

    // ── T-C-05: TASK_ASSIGNED dispatches notification to assignee ──────────

    @Test
    void consume_taskAssigned_createsNotificationForAssignee() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        String message = buildEvent(eventId.toString(), "TaskAssigned", tenantId.toString(),
                taskId.toString(), null, assigneeId.toString(), actorId.toString(), null);

        when(processedEventRepo.existsByEventId(eventId.toString())).thenReturn(false);
        Notification mockNotif = Notification.create(tenantId, assigneeId,
                NotificationType.TASK_ASSIGNED, taskId, "Task was assigned to you");
        when(notificationService.create(any(), any(), any(), any(), any())).thenReturn(mockNotif);

        consumer.consume(message);

        verify(notificationService).create(tenantId, assigneeId,
                NotificationType.TASK_ASSIGNED, taskId, "Task was assigned to you");
        verify(processedEventRepo).save(any(ProcessedEventJpaEntity.class));
        verify(notificationPushPort).pushToUser(any(UUID.class), any());
    }

    @Test
    void consume_taskStatusChanged_createsNotificationForAssignee() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        String message = buildEvent(eventId.toString(), "TaskStatusChanged", tenantId.toString(),
                taskId.toString(), "IN_PROGRESS", assigneeId.toString(), actorId.toString(), "TODO");

        when(processedEventRepo.existsByEventId(eventId.toString())).thenReturn(false);
        Notification mockNotif = Notification.create(tenantId, assigneeId,
                NotificationType.TASK_STATUS_CHANGED, taskId, "Task status changed to IN_PROGRESS");
        when(notificationService.create(any(), any(), any(), any(), any())).thenReturn(mockNotif);

        consumer.consume(message);

        verify(notificationService).create(tenantId, assigneeId,
                NotificationType.TASK_STATUS_CHANGED, taskId, "Task status changed to IN_PROGRESS");
        verify(notificationPushPort).pushToUser(any(UUID.class), any());
    }

    // ── T-C-05: Idempotency — duplicate events are skipped ──────────────────

    @Test
    void consume_duplicateEvent_skipsProcessingAndDoesNotCreateNotification() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        String message = buildEvent(eventId.toString(), "TaskAssigned", tenantId.toString(),
                taskId.toString(), null, assigneeId.toString(), actorId.toString(), null);

        when(processedEventRepo.existsByEventId(eventId.toString())).thenReturn(true);

        consumer.consume(message);

        verify(notificationService, never()).create(any(), any(), any(), any(), any());
        verify(processedEventRepo, never()).save(any());
        verify(notificationPushPort, never()).pushToUser(any(), any());
    }

    // ── T-C-05: TASK_DELETED dispatches notification (if assignee present) ─

    @Test
    void consume_taskDeleted_withAssignee_createsNotification() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        String message = buildEvent(eventId.toString(), "TaskDeleted", tenantId.toString(),
                taskId.toString(), null, assigneeId.toString(), actorId.toString(), null);

        when(processedEventRepo.existsByEventId(eventId.toString())).thenReturn(false);
        Notification mockNotif = Notification.create(tenantId, assigneeId,
                NotificationType.TASK_DELETED, taskId, "A task you were assigned to was deleted");
        when(notificationService.create(any(), any(), any(), any(), any())).thenReturn(mockNotif);

        consumer.consume(message);

        verify(notificationService).create(tenantId, assigneeId,
                NotificationType.TASK_DELETED, taskId, "A task you were assigned to was deleted");
        verify(notificationPushPort).pushToUser(any(UUID.class), any());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String buildEvent(String eventId, String eventType, String tenantId,
            String taskId, String newStatus, String assigneeId, String actorId, String oldStatus) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"eventId\":\"").append(eventId).append("\"");
        sb.append(",\"eventType\":\"").append(eventType).append("\"");
        sb.append(",\"tenantId\":\"").append(tenantId).append("\"");
        sb.append(",\"occurredAt\":\"2026-06-04T10:00:00Z\"");
        sb.append(",\"payload\":{");
        sb.append("\"taskId\":\"").append(taskId).append("\"");
        sb.append(",\"projectId\":\"").append(UUID.randomUUID()).append("\"");
        if (assigneeId != null) {
            sb.append(",\"assigneeId\":\"").append(assigneeId).append("\"");
        }
        if (actorId != null) {
            sb.append(",\"actorId\":\"").append(actorId).append("\"");
        }
        sb.append(",\"title\":\"Fix the login bug\"");
        if (newStatus != null) {
            sb.append(",\"newStatus\":\"").append(newStatus).append("\"");
        }
        if (oldStatus != null) {
            sb.append(",\"oldStatus\":\"").append(oldStatus).append("\"");
        }
        sb.append("}}");
        return sb.toString();
    }
}
