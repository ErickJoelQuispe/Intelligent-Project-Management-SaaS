package com.epm.notification.infrastructure.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;
import static org.hamcrest.Matchers.containsString;

import java.time.Instant;
import java.util.UUID;

import com.epm.notification.application.usecase.NotificationApplicationService;
import com.epm.notification.domain.model.Notification;
import com.epm.notification.domain.model.NotificationType;
import com.epm.notification.infrastructure.adapter.in.messaging.UserEventConsumer;
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
 * Unit tests for UserEventConsumer — guard-based idempotency + poison-message handling.
 *
 * <p>All dependencies mocked — no Kafka broker or Spring context required.
 *
 * <p>Consumer now listens to {@code user.team.member-joined} and {@code user.team.member-left}.
 * Event types are {@code TeamMemberJoined} and {@code TeamMemberLeft} (matching user-service output).
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Normal dispatch (TeamMemberJoined, TeamMemberLeft)</li>
 *   <li>Duplicate event skip via {@link ProcessedEventJpaRepository#claimEvent} returning {@code 0}</li>
 *   <li>Poison messages (missing required fields) — discarded, no NPE, no retry</li>
 *   <li>Unknown event type — logged and skipped</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class UserEventConsumerTest {

    @Mock
    private NotificationApplicationService notificationService;

    @Mock
    private ProcessedEventJpaRepository processedEventRepository;

    private UserEventConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        consumer = new UserEventConsumer(notificationService, processedEventRepository, objectMapper);
    }

    // ── TeamMemberJoined → MEMBER_JOINED_TEAM notification ────────────────

    @Test
    void consume_teamMemberJoined_createsNotification() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String message = buildUserEvent(eventId.toString(), "TeamMemberJoined", tenantId.toString(),
                userId.toString(), "Backend Team", null);

        when(processedEventRepository.claimEvent(
                eq(eventId.toString()), eq("user.team.member-joined"), any(Instant.class)))
                .thenReturn(1);
        Notification mockNotif = Notification.create(tenantId, userId,
                NotificationType.MEMBER_JOINED_TEAM, UUID.randomUUID(), "You joined a team");
        when(notificationService.create(any(), any(), eq(NotificationType.MEMBER_JOINED_TEAM), any(), any()))
                .thenReturn(mockNotif);

        consumer.consume(toRecord("user.team.member-joined", message));

        verify(notificationService).create(
                eq(tenantId), eq(userId),
                eq(NotificationType.MEMBER_JOINED_TEAM), any(), any());
    }

    // ── TeamMemberLeft → MEMBER_LEFT_TEAM notification ────────────────────

    @Test
    void consume_teamMemberLeft_createsNotification() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String message = buildUserEvent(eventId.toString(), "TeamMemberLeft", tenantId.toString(),
                userId.toString(), "Backend Team", null);

        when(processedEventRepository.claimEvent(
                eq(eventId.toString()), eq("user.team.member-left"), any(Instant.class)))
                .thenReturn(1);
        Notification mockNotif = Notification.create(tenantId, userId,
                NotificationType.MEMBER_LEFT_TEAM, UUID.randomUUID(), "You left a team");
        when(notificationService.create(any(), any(), eq(NotificationType.MEMBER_LEFT_TEAM), any(), any()))
                .thenReturn(mockNotif);

        consumer.consume(toRecord("user.team.member-left", message));

        verify(notificationService).create(
                eq(tenantId), eq(userId),
                eq(NotificationType.MEMBER_LEFT_TEAM), any(), any());
    }

    // ── teamName in message: joined ───────────────────────────────────────

    @Test
    void consume_teamMemberJoined_messageContainsTeamName() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String message = buildUserEvent(eventId.toString(), "TeamMemberJoined", tenantId.toString(),
                userId.toString(), "Engineering", null);

        when(processedEventRepository.claimEvent(
                eq(eventId.toString()), eq("user.team.member-joined"), any(Instant.class)))
                .thenReturn(1);
        when(notificationService.create(any(), any(), eq(NotificationType.MEMBER_JOINED_TEAM), any(), any()))
                .thenReturn(Notification.create(tenantId, userId,
                        NotificationType.MEMBER_JOINED_TEAM, UUID.randomUUID(), "You joined Engineering"));

        consumer.consume(toRecord("user.team.member-joined", message));

        verify(notificationService).create(
                eq(tenantId), eq(userId),
                eq(NotificationType.MEMBER_JOINED_TEAM), any(),
                argThat(containsString("Engineering")));
    }

    // ── teamName in message: left ─────────────────────────────────────────

    @Test
    void consume_teamMemberLeft_messageContainsTeamName() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String message = buildUserEvent(eventId.toString(), "TeamMemberLeft", tenantId.toString(),
                userId.toString(), "Design Guild", null);

        when(processedEventRepository.claimEvent(
                eq(eventId.toString()), eq("user.team.member-left"), any(Instant.class)))
                .thenReturn(1);
        when(notificationService.create(any(), any(), eq(NotificationType.MEMBER_LEFT_TEAM), any(), any()))
                .thenReturn(Notification.create(tenantId, userId,
                        NotificationType.MEMBER_LEFT_TEAM, UUID.randomUUID(), "You left Design Guild"));

        consumer.consume(toRecord("user.team.member-left", message));

        verify(notificationService).create(
                eq(tenantId), eq(userId),
                eq(NotificationType.MEMBER_LEFT_TEAM), any(),
                argThat(containsString("Design Guild")));
    }

    // ── Idempotency — claimEvent returns 0 → duplicate skipped ────────────

    @Test
    void consume_duplicateEvent_skipsProcessing() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String message = buildUserEvent(eventId.toString(), "TeamMemberJoined", tenantId.toString(),
                userId.toString(), "Backend Team", null);

        when(processedEventRepository.claimEvent(
                eq(eventId.toString()), eq("user.team.member-joined"), any(Instant.class)))
                .thenReturn(0);

        consumer.consume(toRecord("user.team.member-joined", message));

        verify(notificationService, never()).create(any(), any(), any(), any(), any());
    }

    // ── Idempotency: topic is taken from record, not hardcoded ─────────────
    // member-left topic must claim against "user.team.member-left", not "user.team.member-joined"

    @Test
    void consume_memberLeft_claimsWithCorrectTopic() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String message = buildUserEvent(eventId.toString(), "TeamMemberLeft", tenantId.toString(),
                userId.toString(), null, null);

        when(processedEventRepository.claimEvent(
                eq(eventId.toString()), eq("user.team.member-left"), any(Instant.class)))
                .thenReturn(1);
        when(notificationService.create(any(), any(), eq(NotificationType.MEMBER_LEFT_TEAM), any(), any()))
                .thenReturn(Notification.create(tenantId, userId,
                        NotificationType.MEMBER_LEFT_TEAM, UUID.randomUUID(), "You left a team"));

        consumer.consume(toRecord("user.team.member-left", message));

        // Verify claim used the actual record topic, not a hardcoded constant
        verify(processedEventRepository).claimEvent(
                eq(eventId.toString()), eq("user.team.member-left"), any(Instant.class));
    }

    // ── Unknown eventType → no notification, no exception ────────────────

    @Test
    void consume_unknownEventType_logsWarnAndDoesNotThrow() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String message = buildUserEvent(eventId.toString(), "UserPasswordChanged", tenantId.toString(),
                userId.toString(), null, null);

        when(processedEventRepository.claimEvent(
                eq(eventId.toString()), eq("user.team.member-joined"), any(Instant.class)))
                .thenReturn(1);

        consumer.consume(toRecord("user.team.member-joined", message));

        verify(notificationService, never()).create(any(), any(), any(), any(), any());
    }

    // ── Poison: missing userId → discarded, no claim ─────────────────────

    @Test
    void consume_missingUserId_isDiscardedWithoutNPE() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String message = "{\"eventId\":\"" + eventId + "\""
                + ",\"eventType\":\"TeamMemberJoined\""
                + ",\"tenantId\":\"" + tenantId + "\""
                + ",\"payload\":{\"teamName\":\"Backend\"}}";

        consumer.consume(toRecord("user.team.member-joined", message));

        verify(processedEventRepository, never()).claimEvent(any(), any(), any());
        verify(notificationService, never()).create(any(), any(), any(), any(), any());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private ConsumerRecord<String, String> toRecord(String topic, String value) {
        return new ConsumerRecord<>(topic, 0, 0L,
                ConsumerRecord.NO_TIMESTAMP,
                org.apache.kafka.common.record.TimestampType.NO_TIMESTAMP_TYPE,
                ConsumerRecord.NULL_SIZE, ConsumerRecord.NULL_SIZE,
                null, value, new RecordHeaders(), java.util.Optional.empty());
    }

    private String buildUserEvent(String eventId, String eventType, String tenantId,
            String userId, String teamName, String extra) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"eventId\":\"").append(eventId).append("\"");
        sb.append(",\"eventType\":\"").append(eventType).append("\"");
        sb.append(",\"tenantId\":\"").append(tenantId).append("\"");
        sb.append(",\"occurredAt\":\"2026-06-04T10:00:00Z\"");
        sb.append(",\"payload\":{");
        sb.append("\"userId\":\"").append(userId).append("\"");
        if (teamName != null) {
            sb.append(",\"teamName\":\"").append(teamName).append("\"");
        }
        sb.append("}}");
        return sb.toString();
    }
}
