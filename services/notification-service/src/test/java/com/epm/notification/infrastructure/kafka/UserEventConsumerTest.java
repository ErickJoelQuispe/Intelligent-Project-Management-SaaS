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
import com.epm.notification.domain.port.in.CacheUserEmailUseCase;
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
 * <p>Tests cover:
 * <ul>
 *   <li>Normal dispatch (UserRegistered, MemberJoinedTeam, MemberLeftTeam)</li>
 *   <li>Duplicate event skip via {@link ProcessedEventJpaRepository#claimEvent} returning {@code 0}</li>
 *   <li>Poison messages (missing required fields) — discarded, no NPE, no retry (FIX 5)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class UserEventConsumerTest {

    @Mock
    private NotificationApplicationService notificationService;

    @Mock
    private CacheUserEmailUseCase cacheUserEmailUseCase;

    @Mock
    private ProcessedEventJpaRepository processedEventRepository;

    private UserEventConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        consumer = new UserEventConsumer(notificationService, cacheUserEmailUseCase,
                processedEventRepository, objectMapper);
    }

    // ── UserRegistered → cacheUserEmail ────────────────────────────────────

    @Test
    void consume_userRegistered_callsCacheUserEmail() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String message = buildUserEvent(eventId.toString(), "UserRegistered", tenantId.toString(),
                userId.toString(), "john@example.com", null, null, null);

        when(processedEventRepository.claimEvent(eq(eventId.toString()), eq("user.events"), any(Instant.class))).thenReturn(1);

        consumer.consume(toRecord(message));

        verify(cacheUserEmailUseCase).cacheUserEmail(userId, tenantId, "john@example.com");
    }

    @Test
    void consume_userRegistered_doesNotCreateNotification() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String message = buildUserEvent(eventId.toString(), "UserRegistered", tenantId.toString(),
                userId.toString(), "jane@example.com", null, null, null);

        when(processedEventRepository.claimEvent(eq(eventId.toString()), eq("user.events"), any(Instant.class))).thenReturn(1);

        consumer.consume(toRecord(message));

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

        when(processedEventRepository.claimEvent(eq(eventId.toString()), eq("user.events"), any(Instant.class))).thenReturn(1);
        Notification mockNotif = Notification.create(tenantId, userId,
                NotificationType.MEMBER_JOINED_TEAM, UUID.randomUUID(), "You joined a team");
        when(notificationService.create(any(), any(), eq(NotificationType.MEMBER_JOINED_TEAM), any(), any()))
                .thenReturn(mockNotif);

        consumer.consume(toRecord(message));

        verify(notificationService).create(
                eq(tenantId), eq(userId),
                eq(NotificationType.MEMBER_JOINED_TEAM), any(), any());
    }

    // ── MemberLeftTeam → MEMBER_LEFT_TEAM notification ────────────────────

    @Test
    void consume_memberLeftTeam_createsNotification() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String message = buildUserEvent(eventId.toString(), "MemberLeftTeam", tenantId.toString(),
                userId.toString(), null, "Backend Team", "Alpha Project", null);

        when(processedEventRepository.claimEvent(eq(eventId.toString()), eq("user.events"), any(Instant.class))).thenReturn(1);
        Notification mockNotif = Notification.create(tenantId, userId,
                NotificationType.MEMBER_LEFT_TEAM, UUID.randomUUID(), "You left a team");
        when(notificationService.create(any(), any(), eq(NotificationType.MEMBER_LEFT_TEAM), any(), any()))
                .thenReturn(mockNotif);

        consumer.consume(toRecord(message));

        verify(notificationService).create(
                eq(tenantId), eq(userId),
                eq(NotificationType.MEMBER_LEFT_TEAM), any(), any());
    }

    // ── Idempotency — guard returns false → duplicate skipped ───────────────

    @Test
    void consume_duplicateEvent_skipsProcessing() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String message = buildUserEvent(eventId.toString(), "UserRegistered", tenantId.toString(),
                userId.toString(), "dup@example.com", null, null, null);

        when(processedEventRepository.claimEvent(eq(eventId.toString()), eq("user.events"), any(Instant.class))).thenReturn(0);

        consumer.consume(toRecord(message));

        verify(cacheUserEmailUseCase, never()).cacheUserEmail(any(), any(), any());
    }

    // ── Unknown eventType → no notification, no exception ────────────────

    @Test
    void consume_unknownEventType_logsWarnAndDoesNotThrow() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String message = buildUserEvent(eventId.toString(), "UserPasswordChanged", tenantId.toString(),
                userId.toString(), null, null, null, null);

        when(processedEventRepository.claimEvent(eq(eventId.toString()), eq("user.events"), any(Instant.class))).thenReturn(1);

        // Should not throw
        consumer.consume(toRecord(message));

        // Neither cache nor notification should be invoked for unknown types
        verify(cacheUserEmailUseCase, never()).cacheUserEmail(any(), any(), any());
        verify(notificationService, never()).create(any(), any(), any(), any(), any());
    }

    // ── FIX 5 (M1): poison message — missing userId → discarded, no NPE ─────
    //
    // RED: before adding up-front requiredUuid guard, payload.get("userId").asText()
    //      throws NullPointerException (caught, rethrown as RuntimeException).
    //      The test expects NO exception and NO guard.claim() call — it FAILS.
    // GREEN: with the guard, MalformedEventException is caught before claim() — clean discard.

    @Test
    void consume_missingUserId_isDiscardedWithoutNPE() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        // Payload missing userId — poison message
        String message = "{\"eventId\":\"" + eventId + "\""
                + ",\"eventType\":\"UserRegistered\""
                + ",\"tenantId\":\"" + tenantId + "\""
                + ",\"payload\":{"
                + "\"email\":\"missing@example.com\""
                + "}}";

        // Should not throw, should not call claim, should not create notification
        consumer.consume(toRecord(message));

        verify(processedEventRepository, never()).claimEvent(any(), any(), any());
        verify(cacheUserEmailUseCase, never()).cacheUserEmail(any(), any(), any());
        verify(notificationService, never()).create(any(), any(), any(), any(), any());
    }

    // ── FIX 5 (M1): UserRegistered missing email → discarded after claim ─────
    //
    // The email is dispatched in dispatch(), AFTER the idempotency claim. Missing email
    // is a dispatch-level poison — claim happens, but dispatch detects missing email
    // and discards cleanly without retrying or throwing.

    @Test
    void consume_userRegisteredMissingEmail_isDiscardedWithoutNPE() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        // email field missing — poison for UserRegistered
        String message = "{\"eventId\":\"" + eventId + "\""
                + ",\"eventType\":\"UserRegistered\""
                + ",\"tenantId\":\"" + tenantId + "\""
                + ",\"payload\":{\"userId\":\"" + userId + "\"}}";

        when(processedEventRepository.claimEvent(eq(eventId.toString()), eq("user.events"), any(Instant.class))).thenReturn(1);

        // Should not throw, should not call cacheUserEmail
        consumer.consume(toRecord(message));

        verify(cacheUserEmailUseCase, never()).cacheUserEmail(any(), any(), any());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private ConsumerRecord<String, String> toRecord(String value) {
        return new ConsumerRecord<>("user.events", 0, 0L,
                ConsumerRecord.NO_TIMESTAMP, org.apache.kafka.common.record.TimestampType.NO_TIMESTAMP_TYPE,
                ConsumerRecord.NULL_SIZE, ConsumerRecord.NULL_SIZE,
                null, value, new RecordHeaders(), java.util.Optional.empty());
    }

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
