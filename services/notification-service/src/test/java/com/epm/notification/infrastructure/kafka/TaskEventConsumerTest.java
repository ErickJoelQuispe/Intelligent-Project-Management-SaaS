package com.epm.notification.infrastructure.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import com.epm.notification.application.usecase.NotificationApplicationService;
import com.epm.notification.domain.model.Notification;
import com.epm.notification.domain.model.NotificationType;
import com.epm.notification.domain.port.out.NotificationPushPort;
import com.epm.notification.infrastructure.adapter.in.messaging.TaskEventConsumer;
import com.epm.notification.infrastructure.adapter.out.persistence.ProcessedEventJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for TaskEventConsumer — guard-based idempotency + poison-message handling.
 *
 * <p>Tests event routing logic with mocked dependencies — no Kafka infrastructure needed.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Normal dispatch (TaskAssigned, TaskStatusChanged, TaskDeleted, TaskCreated)</li>
 *   <li>Duplicate event skip via {@link ProcessedEventJpaRepository#claimEvent} returning {@code 0}</li>
 *   <li>Poison messages (missing required fields) — discarded, no NPE, no retry (FIX 5)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TaskEventConsumerTest {

    @Mock
    private NotificationApplicationService notificationService;

    @Mock
    private ProcessedEventJpaRepository processedEventRepository;

    @Mock
    private NotificationPushPort notificationPushPort;

    private TaskEventConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        consumer = new TaskEventConsumer(notificationService, processedEventRepository, notificationPushPort, objectMapper);
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

        when(processedEventRepository.claimEvent(eq(eventId.toString()), eq("task.events"), any(Instant.class))).thenReturn(1);
        Notification mockNotif = Notification.create(tenantId, assigneeId,
                NotificationType.TASK_ASSIGNED, taskId, "Task was assigned to you");
        when(notificationService.create(any(), any(), any(), any(), any())).thenReturn(mockNotif);

        consumer.consume(toRecord(message));

        verify(notificationService).create(tenantId, assigneeId,
                NotificationType.TASK_ASSIGNED, taskId, "Task was assigned to you");
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

        when(processedEventRepository.claimEvent(eq(eventId.toString()), eq("task.events"), any(Instant.class))).thenReturn(1);
        Notification mockNotif = Notification.create(tenantId, assigneeId,
                NotificationType.TASK_STATUS_CHANGED, taskId, "Task status changed to IN_PROGRESS");
        when(notificationService.create(any(), any(), any(), any(), any())).thenReturn(mockNotif);

        consumer.consume(toRecord(message));

        verify(notificationService).create(tenantId, assigneeId,
                NotificationType.TASK_STATUS_CHANGED, taskId, "Task status changed to IN_PROGRESS");
        verify(notificationPushPort).pushToUser(any(UUID.class), any());
    }

    // ── T-C-05: Idempotency — guard returns false → duplicate skipped ───────

    @Test
    void consume_duplicateEvent_skipsProcessingAndDoesNotCreateNotification() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        String message = buildEvent(eventId.toString(), "TaskAssigned", tenantId.toString(),
                taskId.toString(), null, assigneeId.toString(), actorId.toString(), null);

        when(processedEventRepository.claimEvent(eq(eventId.toString()), eq("task.events"), any(Instant.class))).thenReturn(0);

        consumer.consume(toRecord(message));

        verify(notificationService, never()).create(any(), any(), any(), any(), any());
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

        when(processedEventRepository.claimEvent(eq(eventId.toString()), eq("task.events"), any(Instant.class))).thenReturn(1);
        Notification mockNotif = Notification.create(tenantId, assigneeId,
                NotificationType.TASK_DELETED, taskId, "A task you were assigned to was deleted");
        when(notificationService.create(any(), any(), any(), any(), any())).thenReturn(mockNotif);

        consumer.consume(toRecord(message));

        verify(notificationService).create(tenantId, assigneeId,
                NotificationType.TASK_DELETED, taskId, "A task you were assigned to was deleted");
        verify(notificationPushPort).pushToUser(any(UUID.class), any());
    }

    // ── FIX 5 (M1): poison message — missing taskId → discarded, no NPE ─────
    //
    // RED: before adding up-front requiredUuid guard, payload.get("taskId").asText()
    //      throws NullPointerException (caught, rethrown as RuntimeException).
    //      The test expects NO exception and NO guard.claim() call — it FAILS.
    // GREEN: with the guard, MalformedEventException is caught before claim() — clean discard.

    @Test
    void consume_missingTaskId_isDiscardedWithoutNPE() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        // Payload missing taskId — poison message
        String message = "{\"eventId\":\"" + eventId + "\""
                + ",\"eventType\":\"TaskAssigned\""
                + ",\"tenantId\":\"" + tenantId + "\""
                + ",\"payload\":{"
                + "\"assigneeId\":\"" + UUID.randomUUID() + "\""
                + "}}";

        // Should not throw, should not call claim, should not create notification
        consumer.consume(toRecord(message));

        verify(processedEventRepository, never()).claimEvent(any(), any(), any());
        verify(notificationService, never()).create(any(), any(), any(), any(), any());
    }

    // ── FIX 5 (M1): missing eventId → discarded without NPE or claim ────────

    @Test
    void consume_missingEventId_isDiscardedWithoutNPE() throws Exception {
        String message = "{\"eventType\":\"TaskAssigned\""
                + ",\"tenantId\":\"" + UUID.randomUUID() + "\""
                + ",\"payload\":{\"taskId\":\"" + UUID.randomUUID() + "\"}}";

        consumer.consume(toRecord(message));

        verify(processedEventRepository, never()).claimEvent(any(), any(), any());
        verify(notificationService, never()).create(any(), any(), any(), any(), any());
    }

    // ── FIX D (round 3): uuidOrNull malformed value → skip-and-warn, no infinite retry ──

    @Test
    void consume_malformedAssigneeId_eventClaimedButNotificationSkipped() throws Exception {
        // A syntactically present but non-UUID assigneeId must NOT throw out of processRecord.
        // Previously UUID.fromString("not-a-uuid") threw IAE inside dispatch() → RuntimeException
        // → Kafka never committed the offset → infinite redelivery (poison loop).
        String eventId = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        String taskId = UUID.randomUUID().toString();
        String message = "{\"eventId\":\"" + eventId + "\""
                + ",\"eventType\":\"TaskAssigned\""
                + ",\"tenantId\":\"" + tenantId + "\""
                + ",\"payload\":{"
                + "\"taskId\":\"" + taskId + "\""
                + ",\"projectId\":\"" + UUID.randomUUID() + "\""
                + ",\"assigneeId\":\"not-a-uuid\""
                + "}}";

        when(processedEventRepository.claimEvent(eq(eventId), any(), any())).thenReturn(1);

        // Must NOT throw — malformed optional UUID is skipped (warn-and-null), event proceeds
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> consumer.consume(toRecord(message)));

        // claimEvent was called (event cleared idempotency gate)
        verify(processedEventRepository).claimEvent(eq(eventId), any(), any());
        // notificationService.create is NOT called when assigneeId is null for TaskAssigned (correct skip)
        verify(notificationService, never()).create(any(), any(), any(), any(), any());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private ConsumerRecord<String, String> toRecord(String value) {
        return new ConsumerRecord<>("task.events", 0, 0L,
                ConsumerRecord.NO_TIMESTAMP, org.apache.kafka.common.record.TimestampType.NO_TIMESTAMP_TYPE,
                ConsumerRecord.NULL_SIZE, ConsumerRecord.NULL_SIZE,
                null, value, new RecordHeaders(), java.util.Optional.empty());
    }

    private String buildEvent(String eventId, String eventType, String tenantId,
            String taskId, String newStatus, String assigneeId, String actorId, String oldStatus) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"eventId\":\"").append(eventId).append("\"");
        sb.append(",\"eventType\":\"").append(eventType).append("\"");
        sb.append(",\"eventVersion\":1");
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
