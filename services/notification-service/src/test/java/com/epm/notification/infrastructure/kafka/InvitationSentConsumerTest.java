package com.epm.notification.infrastructure.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import com.epm.notification.domain.port.out.EmailPort;
import com.epm.notification.infrastructure.adapter.in.messaging.InvitationSentConsumer;
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
 * Unit tests for InvitationSentConsumer — idempotency + poison-message handling.
 *
 * <p>No Kafka broker or Spring context required — all dependencies mocked.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Happy path: valid payload → EmailPort.send() called with correct to/subject/template</li>
 *   <li>Idempotency: duplicate eventId → email NOT sent again</li>
 *   <li>Poison message (null email) → logged + skipped, no exception thrown</li>
 *   <li>Poison message (missing token) → logged + skipped, no exception thrown</li>
 *   <li>Malformed JSON → discarded, no claim, no exception thrown</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class InvitationSentConsumerTest {

    private static final String TOPIC = "user.invitation.sent";

    @Mock
    private EmailPort emailPort;

    @Mock
    private ProcessedEventJpaRepository processedEventRepository;

    private InvitationSentConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        consumer = new InvitationSentConsumer(emailPort, processedEventRepository, objectMapper);
    }

    // ── Happy path ──────────────────────────────────────────────────────────

    @Test
    void consume_validInvitation_sendsEmailWithCorrectFields() {
        UUID eventId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        String email = "invited@example.com";
        String token = "abc123token";
        String expiresAt = "2026-07-06T18:00:00Z";

        String message = buildInvitationSentEvent(eventId.toString(), invitationId.toString(),
                tenantId.toString(), teamId.toString(), email, token, expiresAt);

        when(processedEventRepository.claimEvent(eq(eventId.toString()), eq(TOPIC), any(Instant.class)))
                .thenReturn(1);

        consumer.consume(toRecord(message));

        verify(emailPort).send(
                eq(email),
                eq("You've been invited to join a workspace"),
                eq("invitation-v1"),
                any()
        );
    }

    @Test
    void consume_validInvitation_acceptUrlContainsTokenAndEmail() {
        UUID eventId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        String email = "user@domain.com";
        String token = "mySecretToken";
        String expiresAt = "2026-07-06T18:00:00Z";

        String message = buildInvitationSentEvent(eventId.toString(), invitationId.toString(),
                tenantId.toString(), teamId.toString(), email, token, expiresAt);

        when(processedEventRepository.claimEvent(eq(eventId.toString()), eq(TOPIC), any(Instant.class)))
                .thenReturn(1);

        consumer.consume(toRecord(message));

        verify(emailPort).send(
                eq(email),
                any(String.class),
                any(String.class),
                org.mockito.ArgumentMatchers.argThat(vars ->
                        vars.containsKey("acceptUrl")
                        && vars.get("acceptUrl").toString().contains(token)
                        && vars.get("acceptUrl").toString().contains("accept-invitation"))
        );
    }

    // ── Idempotency — duplicate skipped ─────────────────────────────────────

    @Test
    void consume_duplicateEvent_emailNotSentAgain() {
        UUID eventId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        String email = "dup@example.com";
        String token = "duptoken";
        String expiresAt = "2026-07-06T18:00:00Z";

        String message = buildInvitationSentEvent(eventId.toString(), invitationId.toString(),
                tenantId.toString(), teamId.toString(), email, token, expiresAt);

        when(processedEventRepository.claimEvent(eq(eventId.toString()), eq(TOPIC), any(Instant.class)))
                .thenReturn(0);

        consumer.consume(toRecord(message));

        verify(emailPort, never()).send(any(), any(), any(), any());
    }

    // ── Poison: null/missing email → skip, no exception ─────────────────────

    @Test
    void consume_missingEmail_skippedWithoutException() {
        UUID eventId = UUID.randomUUID();
        String message = "{\"eventId\":\"" + eventId + "\""
                + ",\"eventType\":\"InvitationSent\""
                + ",\"tenantId\":\"" + UUID.randomUUID() + "\""
                + ",\"payload\":{"
                + "\"invitationId\":\"" + UUID.randomUUID() + "\""
                + ",\"teamId\":\"" + UUID.randomUUID() + "\""
                + ",\"token\":\"sometoken\""
                + ",\"expiresAt\":\"2026-07-06T18:00:00Z\""
                + "}}";

        // Must not throw, must not send email, must not claim
        consumer.consume(toRecord(message));

        verify(processedEventRepository, never()).claimEvent(any(), any(), any());
        verify(emailPort, never()).send(any(), any(), any(), any());
    }

    // ── Poison: missing token → skip, no exception ──────────────────────────

    @Test
    void consume_missingToken_skippedWithoutException() {
        UUID eventId = UUID.randomUUID();
        String message = "{\"eventId\":\"" + eventId + "\""
                + ",\"eventType\":\"InvitationSent\""
                + ",\"tenantId\":\"" + UUID.randomUUID() + "\""
                + ",\"payload\":{"
                + "\"invitationId\":\"" + UUID.randomUUID() + "\""
                + ",\"teamId\":\"" + UUID.randomUUID() + "\""
                + ",\"email\":\"user@example.com\""
                + ",\"expiresAt\":\"2026-07-06T18:00:00Z\""
                + "}}";

        consumer.consume(toRecord(message));

        verify(processedEventRepository, never()).claimEvent(any(), any(), any());
        verify(emailPort, never()).send(any(), any(), any(), any());
    }

    // ── Poison: malformed JSON → discard, no claim ──────────────────────────

    @Test
    void consume_malformedJson_discardedWithoutException() {
        consumer.consume(toRecord("not-json{{{{"));

        verify(processedEventRepository, never()).claimEvent(any(), any(), any());
        verify(emailPort, never()).send(any(), any(), any(), any());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private ConsumerRecord<String, String> toRecord(String value) {
        return new ConsumerRecord<>(TOPIC, 0, 0L,
                ConsumerRecord.NO_TIMESTAMP,
                org.apache.kafka.common.record.TimestampType.NO_TIMESTAMP_TYPE,
                ConsumerRecord.NULL_SIZE, ConsumerRecord.NULL_SIZE,
                null, value, new RecordHeaders(), java.util.Optional.empty());
    }

    private String buildInvitationSentEvent(String eventId, String invitationId,
            String tenantId, String teamId, String email, String token, String expiresAt) {
        return "{\"eventId\":\"" + eventId + "\""
                + ",\"eventType\":\"InvitationSent\""
                + ",\"eventVersion\":1"
                + ",\"tenantId\":\"" + tenantId + "\""
                + ",\"occurredAt\":\"2026-06-29T18:00:00Z\""
                + ",\"aggregateId\":\"" + invitationId + "\""
                + ",\"aggregateType\":\"Invitation\""
                + ",\"payload\":{"
                + "\"invitationId\":\"" + invitationId + "\""
                + ",\"tenantId\":\"" + tenantId + "\""
                + ",\"teamId\":\"" + teamId + "\""
                + ",\"email\":\"" + email + "\""
                + ",\"token\":\"" + token + "\""
                + ",\"expiresAt\":\"" + expiresAt + "\""
                + "}}";
    }
}
