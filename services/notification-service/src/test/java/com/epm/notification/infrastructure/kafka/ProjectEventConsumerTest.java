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
import com.epm.notification.domain.port.out.NotificationPushPort;
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
 * <p>Consumer listens to the real granular project-service topics:
 * <ul>
 *   <li>{@code project.project.created}</li>
 *   <li>{@code project.project.archived}</li>
 *   <li>{@code project.team.assigned}</li>
 * </ul>
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Normal dispatch on correct topic (ProjectCreated, TeamAssignedToProject, ProjectArchived)</li>
 *   <li>ProjectArchived payload includes ownerId and name</li>
 *   <li>TeamAssignedToProject payload includes memberIds</li>
 *   <li>Idempotency claim uses record.topic() (not a hardcoded constant)</li>
 *   <li>Duplicate event skip via {@link ProcessedEventJpaRepository#claimEvent} returning {@code 0}</li>
 *   <li>Poison messages (missing required fields) — discarded, no NPE, no retry</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ProjectEventConsumerTest {

    private static final String TOPIC_PROJECT_CREATED  = "project.project.created";
    private static final String TOPIC_PROJECT_ARCHIVED = "project.project.archived";
    private static final String TOPIC_TEAM_ASSIGNED    = "project.team.assigned";

    @Mock
    private NotificationApplicationService notificationService;

    @Mock
    private ProcessedEventJpaRepository processedEventRepository;

    @Mock
    private NotificationPushPort notificationPushPort;

    private ProjectEventConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        consumer = new ProjectEventConsumer(notificationService, processedEventRepository,
                notificationPushPort, objectMapper);
    }

    // ── ProjectCreated on project.project.created ─────────────────────────

    @Test
    void consume_projectCreated_onCorrectTopic_createsNotificationForOwner() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        String message = buildProjectEvent(eventId.toString(), "ProjectCreated",
                tenantId.toString(), projectId.toString(),
                ownerId.toString(), null, null, "My Project");

        when(processedEventRepository.claimEvent(
                eq(eventId.toString()), eq(TOPIC_PROJECT_CREATED), any(Instant.class)))
                .thenReturn(1);
        Notification mockNotif = Notification.create(tenantId, ownerId,
                NotificationType.PROJECT_CREATED, projectId, "Project 'My Project' was created");
        when(notificationService.create(any(), eq(ownerId),
                eq(NotificationType.PROJECT_CREATED), any(), any()))
                .thenReturn(mockNotif);

        consumer.consume(toRecord(TOPIC_PROJECT_CREATED, message));

        verify(processedEventRepository).claimEvent(
                eq(eventId.toString()), eq(TOPIC_PROJECT_CREATED), any(Instant.class));
        verify(notificationService).create(
                eq(tenantId), eq(ownerId),
                eq(NotificationType.PROJECT_CREATED), any(), any());
    }

    // ── ProjectArchived on project.project.archived — payload has ownerId+name ──

    @Test
    void consume_projectArchived_onCorrectTopic_createsNotificationForOwner() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        String message = buildProjectEvent(eventId.toString(), "ProjectArchived",
                tenantId.toString(), projectId.toString(),
                ownerId.toString(), null, null, "Archived Project");

        when(processedEventRepository.claimEvent(
                eq(eventId.toString()), eq(TOPIC_PROJECT_ARCHIVED), any(Instant.class)))
                .thenReturn(1);
        Notification mockNotif = Notification.create(tenantId, ownerId,
                NotificationType.PROJECT_ARCHIVED, projectId, "Project 'Archived Project' was archived");
        when(notificationService.create(any(), eq(ownerId),
                eq(NotificationType.PROJECT_ARCHIVED), any(), any()))
                .thenReturn(mockNotif);

        consumer.consume(toRecord(TOPIC_PROJECT_ARCHIVED, message));

        verify(processedEventRepository).claimEvent(
                eq(eventId.toString()), eq(TOPIC_PROJECT_ARCHIVED), any(Instant.class));
        verify(notificationService).create(
                eq(tenantId), eq(ownerId),
                eq(NotificationType.PROJECT_ARCHIVED), any(), any());
    }

    @Test
    void consume_projectArchived_withoutOwnerId_skipsNotification() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        // Payload without ownerId (legacy or partial payload) — must not throw
        String message = buildProjectEvent(eventId.toString(), "ProjectArchived",
                tenantId.toString(), projectId.toString(),
                null, null, null, "No Owner Project");

        when(processedEventRepository.claimEvent(
                eq(eventId.toString()), eq(TOPIC_PROJECT_ARCHIVED), any(Instant.class)))
                .thenReturn(1);

        consumer.consume(toRecord(TOPIC_PROJECT_ARCHIVED, message));

        verify(notificationService, never()).create(any(), any(), any(), any(), any());
    }

    // ── TeamAssignedToProject on project.team.assigned — memberIds array ──

    @Test
    void consume_teamAssignedToProject_onCorrectTopic_createsNotificationForEachMember() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID member1 = UUID.randomUUID();
        UUID member2 = UUID.randomUUID();

        String memberIds = "[\"" + member1 + "\",\"" + member2 + "\"]";
        String message = buildTeamAssignedEvent(eventId.toString(), tenantId.toString(),
                projectId.toString(), memberIds);

        when(processedEventRepository.claimEvent(
                eq(eventId.toString()), eq(TOPIC_TEAM_ASSIGNED), any(Instant.class)))
                .thenReturn(1);
        Notification mockNotif = Notification.create(tenantId, member1,
                NotificationType.TEAM_ASSIGNED_TO_PROJECT, projectId, "You have been assigned to a project");
        when(notificationService.create(any(), any(),
                eq(NotificationType.TEAM_ASSIGNED_TO_PROJECT), any(), any()))
                .thenReturn(mockNotif);

        consumer.consume(toRecord(TOPIC_TEAM_ASSIGNED, message));

        verify(processedEventRepository).claimEvent(
                eq(eventId.toString()), eq(TOPIC_TEAM_ASSIGNED), any(Instant.class));
        verify(notificationService, times(2)).create(
                eq(tenantId), any(),
                eq(NotificationType.TEAM_ASSIGNED_TO_PROJECT), any(), any());
    }

    // ── Idempotency: topic from record is used, not a hardcoded constant ──

    @Test
    void consume_idempotencyClaimUses_recordTopic() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        String message = buildProjectEvent(eventId.toString(), "ProjectCreated",
                tenantId.toString(), projectId.toString(),
                ownerId.toString(), null, null, "Topic Check");

        when(processedEventRepository.claimEvent(
                eq(eventId.toString()), eq(TOPIC_PROJECT_CREATED), any(Instant.class)))
                .thenReturn(0); // duplicate

        consumer.consume(toRecord(TOPIC_PROJECT_CREATED, message));

        // Claim was made with the actual record topic, not a hardcoded "project.events"
        verify(processedEventRepository).claimEvent(
                eq(eventId.toString()), eq(TOPIC_PROJECT_CREATED), any(Instant.class));
        verify(notificationService, never()).create(any(), any(), any(), any(), any());
    }

    // ── Duplicate event → skip ────────────────────────────────────────────

    @Test
    void consume_duplicateEvent_skipsProcessing() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        String message = buildProjectEvent(eventId.toString(), "ProjectCreated",
                tenantId.toString(), projectId.toString(),
                ownerId.toString(), null, null, "Dup Project");

        when(processedEventRepository.claimEvent(
                eq(eventId.toString()), eq(TOPIC_PROJECT_CREATED), any(Instant.class)))
                .thenReturn(0);

        consumer.consume(toRecord(TOPIC_PROJECT_CREATED, message));

        verify(notificationService, never()).create(any(), any(), any(), any(), any());
    }

    // ── Unknown eventType → no notification, no exception ────────────────

    @Test
    void consume_unknownEventType_logsWarnAndDoesNotThrow() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        String message = buildProjectEvent(eventId.toString(), "ProjectRenamed",
                tenantId.toString(), projectId.toString(),
                UUID.randomUUID().toString(), null, null, "Some Project");

        when(processedEventRepository.claimEvent(
                eq(eventId.toString()), eq(TOPIC_PROJECT_CREATED), any(Instant.class)))
                .thenReturn(1);

        consumer.consume(toRecord(TOPIC_PROJECT_CREATED, message));

        verify(notificationService, never()).create(any(), any(), any(), any(), any());
    }

    // ── Poison: missing projectId → discarded, no claim ──────────────────

    @Test
    void consume_missingProjectId_isDiscardedWithoutNPE() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String message = "{\"eventId\":\"" + eventId + "\""
                + ",\"eventType\":\"ProjectCreated\""
                + ",\"tenantId\":\"" + tenantId + "\""
                + ",\"payload\":{"
                + "\"ownerId\":\"" + UUID.randomUUID() + "\""
                + "}}";

        consumer.consume(toRecord(TOPIC_PROJECT_CREATED, message));

        verify(processedEventRepository, never()).claimEvent(any(), any(), any());
        verify(notificationService, never()).create(any(), any(), any(), any(), any());
    }

    // ── Poison: missing eventId → discarded without claim ────────────────

    @Test
    void consume_missingEventId_isDiscardedWithoutNPE() {
        String message = "{\"eventType\":\"ProjectCreated\""
                + ",\"tenantId\":\"" + UUID.randomUUID() + "\""
                + ",\"payload\":{\"projectId\":\"" + UUID.randomUUID() + "\"}}";

        consumer.consume(toRecord(TOPIC_PROJECT_CREATED, message));

        verify(processedEventRepository, never()).claimEvent(any(), any(), any());
        verify(notificationService, never()).create(any(), any(), any(), any(), any());
    }

    // ── Poison loop guard: malformed member UUID skipped, valid ones notified ──

    @Test
    void consume_teamAssigned_withOneMalformedMemberId_skipsBadEntry_andNotifiesValidMembers() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID validMember1 = UUID.randomUUID();
        UUID validMember2 = UUID.randomUUID();

        String memberIds = "[\"" + validMember1 + "\",\"not-a-uuid\",\"" + validMember2 + "\"]";
        String message = buildTeamAssignedEvent(eventId.toString(), tenantId.toString(),
                projectId.toString(), memberIds);

        when(processedEventRepository.claimEvent(
                eq(eventId.toString()), eq(TOPIC_TEAM_ASSIGNED), any(Instant.class)))
                .thenReturn(1);
        Notification mockNotif = Notification.create(tenantId, validMember1,
                NotificationType.TEAM_ASSIGNED_TO_PROJECT, projectId, "You have been assigned to a project");
        when(notificationService.create(any(), any(),
                eq(NotificationType.TEAM_ASSIGNED_TO_PROJECT), any(), any()))
                .thenReturn(mockNotif);

        consumer.consume(toRecord(TOPIC_TEAM_ASSIGNED, message));

        verify(notificationService, times(2)).create(
                eq(tenantId), any(),
                eq(NotificationType.TEAM_ASSIGNED_TO_PROJECT), any(), any());
        verify(notificationService).create(eq(tenantId), eq(validMember1),
                eq(NotificationType.TEAM_ASSIGNED_TO_PROJECT), any(), any());
        verify(notificationService).create(eq(tenantId), eq(validMember2),
                eq(NotificationType.TEAM_ASSIGNED_TO_PROJECT), any(), any());
    }

    // ── Malformed ownerId is skipped (warn-and-null), no infinite retry ──

    @Test
    void consume_malformedOwnerId_isSkippedWithoutThrowingRuntimeException() {
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

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> consumer.consume(toRecord(TOPIC_PROJECT_CREATED, message)));

        verify(processedEventRepository).claimEvent(eq(eventId), any(), any());
        verify(notificationService, never()).create(any(), any(), any(), any(), any());
    }

    // ── Bug 1 fix: pushToUser called after ProjectCreated ────────────────────

    @Test
    void consume_projectCreated_pushesToOwnerAfterPersist() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        String message = buildProjectEvent(eventId.toString(), "ProjectCreated",
                tenantId.toString(), projectId.toString(),
                ownerId.toString(), null, null, "Push Project");

        when(processedEventRepository.claimEvent(
                eq(eventId.toString()), eq(TOPIC_PROJECT_CREATED), any(Instant.class)))
                .thenReturn(1);
        Notification mockNotif = Notification.create(tenantId, ownerId,
                NotificationType.PROJECT_CREATED, projectId, "Project 'Push Project' was created");
        when(notificationService.create(any(), eq(ownerId),
                eq(NotificationType.PROJECT_CREATED), any(), any()))
                .thenReturn(mockNotif);

        consumer.consume(toRecord(TOPIC_PROJECT_CREATED, message));

        verify(notificationPushPort).pushToUser(eq(ownerId), any());
    }

    // ── Bug 1 fix: pushToUser called after ProjectArchived ───────────────────

    @Test
    void consume_projectArchived_pushesToOwnerAfterPersist() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        String message = buildProjectEvent(eventId.toString(), "ProjectArchived",
                tenantId.toString(), projectId.toString(),
                ownerId.toString(), null, null, "Archived Project");

        when(processedEventRepository.claimEvent(
                eq(eventId.toString()), eq(TOPIC_PROJECT_ARCHIVED), any(Instant.class)))
                .thenReturn(1);
        Notification mockNotif = Notification.create(tenantId, ownerId,
                NotificationType.PROJECT_ARCHIVED, projectId, "Project 'Archived Project' was archived");
        when(notificationService.create(any(), eq(ownerId),
                eq(NotificationType.PROJECT_ARCHIVED), any(), any()))
                .thenReturn(mockNotif);

        consumer.consume(toRecord(TOPIC_PROJECT_ARCHIVED, message));

        verify(notificationPushPort).pushToUser(eq(ownerId), any());
    }

    // ── Bug 1 fix: pushToUser called for each member in TeamAssignedToProject ─

    @Test
    void consume_teamAssignedToProject_pushesToEachMemberAfterPersist() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID member1 = UUID.randomUUID();
        UUID member2 = UUID.randomUUID();

        String memberIds = "[\"" + member1 + "\",\"" + member2 + "\"]";
        String message = buildTeamAssignedEvent(eventId.toString(), tenantId.toString(),
                projectId.toString(), memberIds);

        when(processedEventRepository.claimEvent(
                eq(eventId.toString()), eq(TOPIC_TEAM_ASSIGNED), any(Instant.class)))
                .thenReturn(1);
        Notification mockNotif = Notification.create(tenantId, member1,
                NotificationType.TEAM_ASSIGNED_TO_PROJECT, projectId, "You have been assigned to a project");
        when(notificationService.create(any(), any(),
                eq(NotificationType.TEAM_ASSIGNED_TO_PROJECT), any(), any()))
                .thenReturn(mockNotif);

        consumer.consume(toRecord(TOPIC_TEAM_ASSIGNED, message));

        verify(notificationPushPort, times(2)).pushToUser(any(UUID.class), any());
    }

    // ── Bug 1 fix: duplicate event → pushToUser never called ─────────────────

    @Test
    void consume_duplicateEvent_doesNotPushToUser() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        String message = buildProjectEvent(eventId.toString(), "ProjectCreated",
                tenantId.toString(), projectId.toString(),
                ownerId.toString(), null, null, "Dup Push Project");

        when(processedEventRepository.claimEvent(
                eq(eventId.toString()), eq(TOPIC_PROJECT_CREATED), any(Instant.class)))
                .thenReturn(0);

        consumer.consume(toRecord(TOPIC_PROJECT_CREATED, message));

        verify(notificationPushPort, never()).pushToUser(any(), any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ConsumerRecord<String, String> toRecord(String topic, String value) {
        return new ConsumerRecord<>(topic, 0, 0L,
                ConsumerRecord.NO_TIMESTAMP,
                org.apache.kafka.common.record.TimestampType.NO_TIMESTAMP_TYPE,
                ConsumerRecord.NULL_SIZE, ConsumerRecord.NULL_SIZE,
                null, value, new RecordHeaders(), java.util.Optional.empty());
    }

    private String buildProjectEvent(String eventId, String eventType, String tenantId,
            String projectId, String ownerId, String memberIds, String teamId, String name) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"eventId\":\"").append(eventId).append("\"");
        sb.append(",\"eventType\":\"").append(eventType).append("\"");
        sb.append(",\"eventVersion\":1");
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
                + ",\"eventVersion\":1"
                + ",\"tenantId\":\"" + tenantId + "\""
                + ",\"occurredAt\":\"2026-06-04T10:00:00Z\""
                + ",\"payload\":{"
                + "\"projectId\":\"" + projectId + "\""
                + ",\"memberIds\":" + memberIds
                + "}}";
    }
}
