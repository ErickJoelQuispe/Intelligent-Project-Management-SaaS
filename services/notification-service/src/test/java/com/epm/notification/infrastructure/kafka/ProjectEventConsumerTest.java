package com.epm.notification.infrastructure.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import com.epm.notification.application.usecase.NotificationApplicationService;
import com.epm.notification.domain.model.Notification;
import com.epm.notification.domain.model.NotificationType;
import com.epm.notification.infrastructure.adapter.in.messaging.ProjectEventConsumer;
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
 * Unit tests for ProjectEventConsumer — guard-based idempotency + poison-message handling.
 *
 * <p>All dependencies mocked — no Kafka broker or Spring context required.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Normal dispatch (ProjectCreated, TeamAssignedToProject, ProjectArchived)</li>
 *   <li>Duplicate event skip via {@link ProcessedEventJpaRepository#claimEvent} returning {@code 0}</li>
 *   <li>Poison messages (missing required fields) — discarded, no NPE, no retry (FIX 5)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ProjectEventConsumerTest {

    @Mock
    private NotificationApplicationService notificationService;

    @Mock
    private ProcessedEventJpaRepository processedEventRepository;

    private ProjectEventConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        consumer = new ProjectEventConsumer(notificationService, processedEventRepository, objectMapper);
    }

    // ── ProjectCreated → PROJECT_CREATED notification for ownerId ─────────

    @Test
    void consume_projectCreated_createsNotificationForOwner() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        String message = buildProjectEvent(eventId.toString(), "ProjectCreated",
                tenantId.toString(), projectId.toString(),
                ownerId.toString(), null, null, "My Project");

        when(processedEventRepository.claimEvent(eq(eventId.toString()), eq("project.events"), any(Instant.class))).thenReturn(1);
        Notification mockNotif = Notification.create(tenantId, ownerId,
                NotificationType.PROJECT_CREATED, projectId, "Project 'My Project' was created");
        when(notificationService.create(any(), eq(ownerId),
                eq(NotificationType.PROJECT_CREATED), any(), any()))
                .thenReturn(mockNotif);

        consumer.consume(toRecord(message));

        verify(notificationService).create(
                eq(tenantId), eq(ownerId),
                eq(NotificationType.PROJECT_CREATED), any(), any());
    }

    // ── TeamAssignedToProject → TEAM_ASSIGNED_TO_PROJECT for each member ──

    @Test
    void consume_teamAssignedToProject_createsNotificationForEachMember() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID member1 = UUID.randomUUID();
        UUID member2 = UUID.randomUUID();

        String memberIds = "[\"" + member1 + "\",\"" + member2 + "\"]";
        String message = buildTeamAssignedEvent(eventId.toString(), tenantId.toString(),
                projectId.toString(), memberIds);

        when(processedEventRepository.claimEvent(eq(eventId.toString()), eq("project.events"), any(Instant.class))).thenReturn(1);
        Notification mockNotif = Notification.create(tenantId, member1,
                NotificationType.TEAM_ASSIGNED_TO_PROJECT, projectId, "You have been assigned to a project");
        when(notificationService.create(any(), any(),
                eq(NotificationType.TEAM_ASSIGNED_TO_PROJECT), any(), any()))
                .thenReturn(mockNotif);

        consumer.consume(toRecord(message));

        verify(notificationService, times(2)).create(
                eq(tenantId), any(),
                eq(NotificationType.TEAM_ASSIGNED_TO_PROJECT), any(), any());
    }

    // ── ProjectArchived → PROJECT_ARCHIVED notification for ownerId ────────

    @Test
    void consume_projectArchived_createsNotificationForOwner() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        String message = buildProjectEvent(eventId.toString(), "ProjectArchived",
                tenantId.toString(), projectId.toString(),
                ownerId.toString(), null, null, "Archived Project");

        when(processedEventRepository.claimEvent(eq(eventId.toString()), eq("project.events"), any(Instant.class))).thenReturn(1);
        Notification mockNotif = Notification.create(tenantId, ownerId,
                NotificationType.PROJECT_ARCHIVED, projectId, "Project 'Archived Project' was archived");
        when(notificationService.create(any(), eq(ownerId),
                eq(NotificationType.PROJECT_ARCHIVED), any(), any()))
                .thenReturn(mockNotif);

        consumer.consume(toRecord(message));

        verify(notificationService).create(
                eq(tenantId), eq(ownerId),
                eq(NotificationType.PROJECT_ARCHIVED), any(), any());
    }

    // ── Duplicate event → guard returns false → business skipped ─────────

    @Test
    void consume_duplicateEvent_skipsProcessing() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        String message = buildProjectEvent(eventId.toString(), "ProjectCreated",
                tenantId.toString(), projectId.toString(),
                ownerId.toString(), null, null, "Dup Project");

        when(processedEventRepository.claimEvent(eq(eventId.toString()), eq("project.events"), any(Instant.class))).thenReturn(0);

        consumer.consume(toRecord(message));

        verify(notificationService, never()).create(any(), any(), any(), any(), any());
    }

    // ── Unknown eventType → no notification, no exception ────────────────

    @Test
    void consume_unknownEventType_logsWarnAndDoesNotThrow() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        String message = buildProjectEvent(eventId.toString(), "ProjectRenamed",
                tenantId.toString(), projectId.toString(),
                UUID.randomUUID().toString(), null, null, "Some Project");

        when(processedEventRepository.claimEvent(eq(eventId.toString()), eq("project.events"), any(Instant.class))).thenReturn(1);

        // Should NOT throw
        consumer.consume(toRecord(message));

        verify(notificationService, never()).create(any(), any(), any(), any(), any());
    }

    // ── FIX 5 (M1): poison message — missing projectId → discarded, no NPE ──
    //
    // RED: before adding up-front requiredUuid guard, payload.get("projectId").asText()
    //      throws NullPointerException (wrapped in RuntimeException and rethrown).
    //      The test expects NO exception and NO guard.claim() call — it FAILS.
    // GREEN: with the guard, MalformedEventException is caught before claim() — clean discard.

    @Test
    void consume_missingProjectId_isDiscardedWithoutNPE() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        // Payload missing projectId — poison message
        String message = "{\"eventId\":\"" + eventId + "\""
                + ",\"eventType\":\"ProjectCreated\""
                + ",\"tenantId\":\"" + tenantId + "\""
                + ",\"payload\":{"
                + "\"ownerId\":\"" + UUID.randomUUID() + "\""
                + "}}";

        // Should not throw, should not call claim, should not create notification
        consumer.consume(toRecord(message));

        verify(processedEventRepository, never()).claimEvent(any(), any(), any());
        verify(notificationService, never()).create(any(), any(), any(), any(), any());
    }

    // ── FIX D (poison loop): malformed member UUID must NOT throw / NOT retry ──
    //
    // parseMemberIds did `UUID.fromString(val)` unguarded. Called from dispatch() inside the
    // inner try that rethrows as RuntimeException → Kafka offset NOT committed → infinite
    // redelivery (poison loop) on ONE malformed member id.
    //
    // Strategy: SKIP the bad member id (log WARN), still process the valid ones. A single bad
    // member must not poison the whole event nor discard notifications for the valid members.
    //
    // RED before the guard: UUID.fromString("not-a-uuid") throws IllegalArgumentException →
    //   wrapped in RuntimeException by dispatch's catch → propagates out of consume() →
    //   `consumer.consume(...)` THROWS → the test (which expects no throw) FAILS.
    // GREEN after the guard: the bad entry is skipped, the two valid members are notified.

    @Test
    void consume_teamAssigned_withOneMalformedMemberId_skipsBadEntry_andNotifiesValidMembers() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID validMember1 = UUID.randomUUID();
        UUID validMember2 = UUID.randomUUID();

        // memberIds = [valid, "not-a-uuid", valid] — one poison entry in the middle.
        String memberIds = "[\"" + validMember1 + "\",\"not-a-uuid\",\"" + validMember2 + "\"]";
        String message = buildTeamAssignedEvent(eventId.toString(), tenantId.toString(),
                projectId.toString(), memberIds);

        when(processedEventRepository.claimEvent(eq(eventId.toString()), eq("project.events"), any(Instant.class)))
                .thenReturn(1);
        Notification mockNotif = Notification.create(tenantId, validMember1,
                NotificationType.TEAM_ASSIGNED_TO_PROJECT, projectId, "You have been assigned to a project");
        when(notificationService.create(any(), any(),
                eq(NotificationType.TEAM_ASSIGNED_TO_PROJECT), any(), any()))
                .thenReturn(mockNotif);

        // Must NOT throw (no poison loop). A throw here is the RED signal.
        consumer.consume(toRecord(message));

        // The two valid members are still notified; the malformed entry is silently skipped.
        verify(notificationService, times(2)).create(
                eq(tenantId), any(),
                eq(NotificationType.TEAM_ASSIGNED_TO_PROJECT), any(), any());
        verify(notificationService).create(eq(tenantId), eq(validMember1),
                eq(NotificationType.TEAM_ASSIGNED_TO_PROJECT), any(), any());
        verify(notificationService).create(eq(tenantId), eq(validMember2),
                eq(NotificationType.TEAM_ASSIGNED_TO_PROJECT), any(), any());
    }

    // ── FIX 5 (M1): missing eventId → discarded without NPE or claim ──────

    @Test
    void consume_missingEventId_isDiscardedWithoutNPE() throws Exception {
        String message = "{\"eventType\":\"ProjectCreated\""
                + ",\"tenantId\":\"" + UUID.randomUUID() + "\""
                + ",\"payload\":{\"projectId\":\"" + UUID.randomUUID() + "\"}}";

        consumer.consume(toRecord(message));

        verify(processedEventRepository, never()).claimEvent(any(), any(), any());
        verify(notificationService, never()).create(any(), any(), any(), any(), any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ConsumerRecord<String, String> toRecord(String value) {
        return new ConsumerRecord<>("project.events", 0, 0L,
                ConsumerRecord.NO_TIMESTAMP, org.apache.kafka.common.record.TimestampType.NO_TIMESTAMP_TYPE,
                ConsumerRecord.NULL_SIZE, ConsumerRecord.NULL_SIZE,
                null, value, new RecordHeaders(), java.util.Optional.empty());
    }

    private String buildProjectEvent(String eventId, String eventType, String tenantId,
            String projectId, String ownerId, String memberIds, String teamId, String name) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"eventId\":\"").append(eventId).append("\"");
        sb.append(",\"eventType\":\"").append(eventType).append("\"");
        sb.append(",\"tenantId\":\"").append(tenantId).append("\"");
        sb.append(",\"occurredAt\":\"2026-06-04T10:00:00Z\"");
        sb.append(",\"payload\":{");
        sb.append("\"projectId\":\"").append(projectId).append("\"");
        if (ownerId != null) {
            sb.append(",\"ownerId\":\"").append(ownerId).append("\"");
        }
        if (name != null) {
            sb.append(",\"name\":\"").append(name).append("\"");
        }
        sb.append("}}");
        return sb.toString();
    }

    private String buildTeamAssignedEvent(String eventId, String tenantId,
            String projectId, String memberIds) {
        return "{\"eventId\":\"" + eventId + "\""
                + ",\"eventType\":\"TeamAssignedToProject\""
                + ",\"tenantId\":\"" + tenantId + "\""
                + ",\"occurredAt\":\"2026-06-04T10:00:00Z\""
                + ",\"payload\":{"
                + "\"projectId\":\"" + projectId + "\""
                + ",\"memberIds\":" + memberIds
                + "}}";
    }

    // ── FIX D (round 3): uuidOrNull malformed value → skip-and-warn, no infinite retry ──

    @Test
    void consume_malformedOwnerId_isSkippedWithoutThrowingRuntimeException() throws Exception {
        // A syntactically present but non-UUID ownerId must NOT throw out of processRecord.
        // Previously UUID.fromString("not-a-uuid") inside uuidOrNull() threw IAE in dispatch()
        // → caught by outer catch → rethrown as RuntimeException → Kafka offset not committed
        // → infinite redelivery (poison loop). This test must be RED without the try/catch guard.
        String eventId = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        String projectId = UUID.randomUUID().toString();
        String message = "{\"eventId\":\"" + eventId + "\""
                + ",\"eventType\":\"ProjectCreated\""
                + ",\"tenantId\":\"" + tenantId + "\""
                + ",\"occurredAt\":\"2026-06-04T10:00:00Z\""
                + ",\"payload\":{"
                + "\"projectId\":\"" + projectId + "\""
                + ",\"name\":\"Test Project\""
                + ",\"ownerId\":\"not-a-uuid\""
                + "}}";

        when(processedEventRepository.claimEvent(eq(eventId), any(), any())).thenReturn(1);

        // Must NOT throw — malformed optional UUID is skipped (warn-and-null), event proceeds
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> consumer.consume(toRecord(message)));

        // claimEvent was called (event cleared idempotency gate)
        verify(processedEventRepository).claimEvent(eq(eventId), any(), any());
        // notificationService.create is NOT called when ownerId is null for ProjectCreated
        verify(notificationService, never()).create(any(), any(), any(), any(), any());
    }
}
