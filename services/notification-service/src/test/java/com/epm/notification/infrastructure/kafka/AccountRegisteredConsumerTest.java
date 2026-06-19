package com.epm.notification.infrastructure.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import com.epm.notification.domain.port.in.CacheUserEmailUseCase;
import com.epm.notification.infrastructure.adapter.in.messaging.AccountRegisteredConsumer;
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
 * Unit tests for AccountRegisteredConsumer — idempotency + poison-message handling.
 *
 * <p>No Kafka broker or Spring context required — all dependencies mocked.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Happy path: AccountRegistered → cacheUserEmail called</li>
 *   <li>Duplicate event (claimEvent returns 0) → skipped</li>
 *   <li>Malformed JSON → discarded, no claim</li>
 *   <li>Missing email → discarded (poison), no claim</li>
 *   <li>Missing accountId → discarded (poison), no claim</li>
 *   <li>cacheUserEmail throws → consumer rethrows</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AccountRegisteredConsumerTest {

    private static final String TOPIC = "auth.account.registered";

    @Mock
    private CacheUserEmailUseCase cacheUserEmailUseCase;

    @Mock
    private ProcessedEventJpaRepository processedEventRepository;

    private AccountRegisteredConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        consumer = new AccountRegisteredConsumer(cacheUserEmailUseCase, processedEventRepository, objectMapper);
    }

    // ── Happy path ──────────────────────────────────────────────────────────

    @Test
    void consume_accountRegistered_callsCacheUserEmail() {
        UUID eventId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String message = buildAccountRegisteredEvent(eventId.toString(), tenantId.toString(),
                accountId.toString(), "user@example.com");

        when(processedEventRepository.claimEvent(eq(eventId.toString()), eq(TOPIC), any(Instant.class)))
                .thenReturn(1);

        consumer.consume(toRecord(message));

        verify(cacheUserEmailUseCase).cacheUserEmail(accountId, tenantId, "user@example.com");
    }

    // ── Idempotency — duplicate skipped ───────────────────────────────────

    @Test
    void consume_accountRegistered_duplicate_skips() {
        UUID eventId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String message = buildAccountRegisteredEvent(eventId.toString(), tenantId.toString(),
                accountId.toString(), "dup@example.com");

        when(processedEventRepository.claimEvent(eq(eventId.toString()), eq(TOPIC), any(Instant.class)))
                .thenReturn(0);

        consumer.consume(toRecord(message));

        verify(cacheUserEmailUseCase, never()).cacheUserEmail(any(), any(), any());
    }

    // ── Poison: malformed JSON → discard, no claim ─────────────────────────

    @Test
    void consume_accountRegistered_malformedJson_discards() {
        consumer.consume(toRecord("not-json-at-all{{{"));

        verify(processedEventRepository, never()).claimEvent(any(), any(), any());
        verify(cacheUserEmailUseCase, never()).cacheUserEmail(any(), any(), any());
    }

    // ── Poison: missing email → discard, no claim ─────────────────────────

    @Test
    void consume_accountRegistered_missingEmail_discards() {
        UUID eventId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        // email field absent from payload
        String message = "{\"eventId\":\"" + eventId + "\""
                + ",\"eventType\":\"AccountRegistered\""
                + ",\"tenantId\":\"" + tenantId + "\""
                + ",\"payload\":{\"accountId\":\"" + accountId + "\"}}";

        consumer.consume(toRecord(message));

        verify(processedEventRepository, never()).claimEvent(any(), any(), any());
        verify(cacheUserEmailUseCase, never()).cacheUserEmail(any(), any(), any());
    }

    // ── Poison: missing accountId → discard, no claim ────────────────────

    @Test
    void consume_accountRegistered_missingAccountId_discards() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        // accountId field absent from payload
        String message = "{\"eventId\":\"" + eventId + "\""
                + ",\"eventType\":\"AccountRegistered\""
                + ",\"tenantId\":\"" + tenantId + "\""
                + ",\"payload\":{\"email\":\"user@example.com\"}}";

        consumer.consume(toRecord(message));

        verify(processedEventRepository, never()).claimEvent(any(), any(), any());
        verify(cacheUserEmailUseCase, never()).cacheUserEmail(any(), any(), any());
    }

    // ── Dispatch failure → consumer rethrows ─────────────────────────────

    @Test
    void consume_accountRegistered_dispatchFails_rethrows() {
        UUID eventId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String message = buildAccountRegisteredEvent(eventId.toString(), tenantId.toString(),
                accountId.toString(), "fail@example.com");

        when(processedEventRepository.claimEvent(eq(eventId.toString()), eq(TOPIC), any(Instant.class)))
                .thenReturn(1);
        doThrow(new RuntimeException("email cache unavailable"))
                .when(cacheUserEmailUseCase).cacheUserEmail(any(), any(), any());

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> consumer.consume(toRecord(message)));
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private ConsumerRecord<String, String> toRecord(String value) {
        return new ConsumerRecord<>(TOPIC, 0, 0L,
                ConsumerRecord.NO_TIMESTAMP,
                org.apache.kafka.common.record.TimestampType.NO_TIMESTAMP_TYPE,
                ConsumerRecord.NULL_SIZE, ConsumerRecord.NULL_SIZE,
                null, value, new RecordHeaders(), java.util.Optional.empty());
    }

    private String buildAccountRegisteredEvent(String eventId, String tenantId,
            String accountId, String email) {
        return "{\"eventId\":\"" + eventId + "\""
                + ",\"eventType\":\"AccountRegistered\""
                + ",\"eventVersion\":1"
                + ",\"tenantId\":\"" + tenantId + "\""
                + ",\"occurredAt\":\"2026-06-04T10:00:00Z\""
                + ",\"aggregateId\":\"" + accountId + "\""
                + ",\"aggregateType\":\"Account\""
                + ",\"payload\":{"
                + "\"accountId\":\"" + accountId + "\""
                + ",\"keycloakUserId\":\"" + UUID.randomUUID() + "\""
                + ",\"email\":\"" + email + "\""
                + ",\"firstName\":\"Test\""
                + ",\"lastName\":\"User\""
                + "}}";
    }
}
