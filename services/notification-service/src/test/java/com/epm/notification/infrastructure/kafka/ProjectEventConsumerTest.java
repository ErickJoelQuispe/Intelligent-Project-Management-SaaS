package com.epm.notification.infrastructure.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.epm.notification.application.usecase.NotificationApplicationService;
import com.epm.notification.domain.model.Notification;
import com.epm.notification.domain.model.NotificationType;
import com.epm.notification.infrastructure.adapter.in.messaging.ProjectEventConsumer;
import com.epm.notification.infrastructure.adapter.out.persistence.ProcessedEventJpaEntity;
import com.epm.notification.infrastructure.adapter.out.persistence.ProcessedEventJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for ProjectEventConsumer (TDD — Strict RED→GREEN→REFACTOR).
 *
 * <p>All dependencies mocked — no Kafka broker or Spring context required.
 */
@ExtendWith(MockitoExtension.class)
class ProjectEventConsumerTest {

    @Mock
    private NotificationApplicationService notificationService;

    @Mock
    private ProcessedEventJpaRepository processedEventRepo;

    private ProjectEventConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        consumer = new ProjectEventConsumer(notificationService, processedEventRepo, objectMapper);
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

        when(processedEventRepo.existsByEventId(eventId.toString())).thenReturn(false);
        Notification mockNotif = Notification.create(tenantId, ownerId,
                NotificationType.PROJECT_CREATED, projectId, "Project 'My Project' was created");
        when(notificationService.create(any(), eq(ownerId),
                eq(NotificationType.PROJECT_CREATED), any(), any()))
                .thenReturn(mockNotif);

        consumer.consume(message);

        verify(notificationService).create(
                eq(tenantId), eq(ownerId),
                eq(NotificationType.PROJECT_CREATED), any(), any());
        verify(processedEventRepo).save(any(ProcessedEventJpaEntity.class));
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

        when(processedEventRepo.existsByEventId(eventId.toString())).thenReturn(false);
        Notification mockNotif = Notification.create(tenantId, member1,
                NotificationType.TEAM_ASSIGNED_TO_PROJECT, projectId, "You have been assigned to a project");
        when(notificationService.create(any(), any(),
                eq(NotificationType.TEAM_ASSIGNED_TO_PROJECT), any(), any()))
                .thenReturn(mockNotif);

        consumer.consume(message);

        verify(notificationService, times(2)).create(
                eq(tenantId), any(),
                eq(NotificationType.TEAM_ASSIGNED_TO_PROJECT), any(), any());
        verify(processedEventRepo).save(any(ProcessedEventJpaEntity.class));
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

        when(processedEventRepo.existsByEventId(eventId.toString())).thenReturn(false);
        Notification mockNotif = Notification.create(tenantId, ownerId,
                NotificationType.PROJECT_ARCHIVED, projectId, "Project 'Archived Project' was archived");
        when(notificationService.create(any(), eq(ownerId),
                eq(NotificationType.PROJECT_ARCHIVED), any(), any()))
                .thenReturn(mockNotif);

        consumer.consume(message);

        verify(notificationService).create(
                eq(tenantId), eq(ownerId),
                eq(NotificationType.PROJECT_ARCHIVED), any(), any());
        verify(processedEventRepo).save(any(ProcessedEventJpaEntity.class));
    }

    // ── Duplicate event (same eventId) → skipped ──────────────────────────

    @Test
    void consume_duplicateEvent_skipsProcessing() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        String message = buildProjectEvent(eventId.toString(), "ProjectCreated",
                tenantId.toString(), projectId.toString(),
                ownerId.toString(), null, null, "Dup Project");

        when(processedEventRepo.existsByEventId(eventId.toString())).thenReturn(true);

        consumer.consume(message);

        verify(notificationService, never()).create(any(), any(), any(), any(), any());
        verify(processedEventRepo, never()).save(any());
    }

    // ── Unknown eventType → WARN logged, no exception ─────────────────────

    @Test
    void consume_unknownEventType_logsWarnAndDoesNotThrow() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        String message = buildProjectEvent(eventId.toString(), "ProjectRenamed",
                tenantId.toString(), projectId.toString(),
                UUID.randomUUID().toString(), null, null, "Some Project");

        when(processedEventRepo.existsByEventId(eventId.toString())).thenReturn(false);

        // Should NOT throw
        consumer.consume(message);

        verify(notificationService, never()).create(any(), any(), any(), any(), any());
        // Processed event is still saved to prevent log spam on re-delivery
        verify(processedEventRepo).save(any(ProcessedEventJpaEntity.class));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
}
