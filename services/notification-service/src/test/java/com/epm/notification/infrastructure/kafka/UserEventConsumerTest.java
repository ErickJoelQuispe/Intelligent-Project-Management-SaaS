package com.epm.notification.infrastructure.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.epm.notification.application.usecase.CacheUserEmailService;
import com.epm.notification.application.usecase.NotificationApplicationService;
import com.epm.notification.domain.model.Notification;
import com.epm.notification.domain.model.NotificationType;
import com.epm.notification.domain.port.in.CacheUserEmailUseCase;
import com.epm.notification.infrastructure.adapter.in.messaging.UserEventConsumer;
import com.epm.notification.infrastructure.adapter.out.persistence.ProcessedEventJpaEntity;
import com.epm.notification.infrastructure.adapter.out.persistence.ProcessedEventJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for UserEventConsumer (TDD — Strict RED→GREEN→REFACTOR).
 *
 * <p>All dependencies mocked — no Kafka broker or Spring context required.
 */
@ExtendWith(MockitoExtension.class)
class UserEventConsumerTest {

    @Mock
    private NotificationApplicationService notificationService;

    @Mock
    private CacheUserEmailUseCase cacheUserEmailUseCase;

    @Mock
    private ProcessedEventJpaRepository processedEventRepo;

    private UserEventConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        consumer = new UserEventConsumer(notificationService, cacheUserEmailUseCase,
                processedEventRepo, objectMapper);
    }

    // ── UserRegistered → cacheUserEmail ────────────────────────────────────

    @Test
    void consume_userRegistered_callsCacheUserEmail() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String message = buildUserEvent(eventId.toString(), "UserRegistered", tenantId.toString(),
                userId.toString(), "john@example.com", null, null, null);

        when(processedEventRepo.existsByEventId(eventId.toString())).thenReturn(false);

        consumer.consume(message);

        verify(cacheUserEmailUseCase).cacheUserEmail(userId, tenantId, "john@example.com");
        verify(processedEventRepo).save(any(ProcessedEventJpaEntity.class));
    }

    @Test
    void consume_userRegistered_doesNotCreateNotification() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String message = buildUserEvent(eventId.toString(), "UserRegistered", tenantId.toString(),
                userId.toString(), "jane@example.com", null, null, null);

        when(processedEventRepo.existsByEventId(eventId.toString())).thenReturn(false);

        consumer.consume(message);

        verify(notificationService, never()).create(any(), any(), any(), any(), any());
    }

    // ── MemberJoinedTeam → MEMBER_JOINED_TEAM notification ────────────────

    @Test
    void consume_memberJoinedTeam_createsNotification() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String message = buildUserEvent(eventId.toString(), "MemberJoinedTeam", tenantId.toString(),
                userId.toString(), null, "Backend Team", "Alpha Project", null);

        when(processedEventRepo.existsByEventId(eventId.toString())).thenReturn(false);
        Notification mockNotif = Notification.create(tenantId, userId,
                NotificationType.MEMBER_JOINED_TEAM, UUID.randomUUID(), "You joined a team");
        when(notificationService.create(any(), any(), eq(NotificationType.MEMBER_JOINED_TEAM), any(), any()))
                .thenReturn(mockNotif);

        consumer.consume(message);

        verify(notificationService).create(
                eq(tenantId), eq(userId),
                eq(NotificationType.MEMBER_JOINED_TEAM), any(), any());
        verify(processedEventRepo).save(any(ProcessedEventJpaEntity.class));
    }

    // ── MemberLeftTeam → MEMBER_LEFT_TEAM notification ────────────────────

    @Test
    void consume_memberLeftTeam_createsNotification() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String message = buildUserEvent(eventId.toString(), "MemberLeftTeam", tenantId.toString(),
                userId.toString(), null, "Backend Team", "Alpha Project", null);

        when(processedEventRepo.existsByEventId(eventId.toString())).thenReturn(false);
        Notification mockNotif = Notification.create(tenantId, userId,
                NotificationType.MEMBER_LEFT_TEAM, UUID.randomUUID(), "You left a team");
        when(notificationService.create(any(), any(), eq(NotificationType.MEMBER_LEFT_TEAM), any(), any()))
                .thenReturn(mockNotif);

        consumer.consume(message);

        verify(notificationService).create(
                eq(tenantId), eq(userId),
                eq(NotificationType.MEMBER_LEFT_TEAM), any(), any());
        verify(processedEventRepo).save(any(ProcessedEventJpaEntity.class));
    }

    // ── Idempotency — duplicate event is skipped ───────────────────────────

    @Test
    void consume_duplicateEvent_skipsProcessing() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String message = buildUserEvent(eventId.toString(), "UserRegistered", tenantId.toString(),
                userId.toString(), "dup@example.com", null, null, null);

        when(processedEventRepo.existsByEventId(eventId.toString())).thenReturn(true);

        consumer.consume(message);

        verify(cacheUserEmailUseCase, never()).cacheUserEmail(any(), any(), any());
        verify(processedEventRepo, never()).save(any());
    }

    // ── Unknown eventType → WARN logged, no exception ─────────────────────

    @Test
    void consume_unknownEventType_logsWarnAndDoesNotThrow() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String message = buildUserEvent(eventId.toString(), "UserPasswordChanged", tenantId.toString(),
                userId.toString(), null, null, null, null);

        when(processedEventRepo.existsByEventId(eventId.toString())).thenReturn(false);

        // Should not throw
        consumer.consume(message);

        // Neither cache nor notification should be invoked for unknown types
        verify(cacheUserEmailUseCase, never()).cacheUserEmail(any(), any(), any());
        verify(notificationService, never()).create(any(), any(), any(), any(), any());
        // processed_events IS still saved to prevent log spam on re-delivery
        verify(processedEventRepo).save(any(ProcessedEventJpaEntity.class));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String buildUserEvent(String eventId, String eventType, String tenantId,
            String userId, String email, String teamName, String projectName, String extra) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"eventId\":\"").append(eventId).append("\"");
        sb.append(",\"eventType\":\"").append(eventType).append("\"");
        sb.append(",\"tenantId\":\"").append(tenantId).append("\"");
        sb.append(",\"occurredAt\":\"2026-06-04T10:00:00Z\"");
        sb.append(",\"payload\":{");
        sb.append("\"userId\":\"").append(userId).append("\"");
        if (email != null) {
            sb.append(",\"email\":\"").append(email).append("\"");
        }
        if (teamName != null) {
            sb.append(",\"teamName\":\"").append(teamName).append("\"");
        }
        if (projectName != null) {
            sb.append(",\"projectName\":\"").append(projectName).append("\"");
        }
        sb.append("}}");
        return sb.toString();
    }
}
