package com.epm.notification.infrastructure.adapter.in.messaging;

import java.time.Instant;
import java.util.UUID;

import com.epm.notification.domain.port.in.CacheUserEmailUseCase;
import com.epm.notification.infrastructure.adapter.out.persistence.ProcessedEventJpaRepository;
import com.epm.notification.infrastructure.messaging.tracing.KafkaTracingSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka consumer for {@code auth.account.registered} topic.
 *
 * <p>Handles:
 * <ul>
 *   <li>{@code AccountRegistered} → caches the user's email via {@link CacheUserEmailUseCase}
 *       using {@code payload.accountId} as the userId</li>
 * </ul>
 *
 * <p><strong>Idempotency</strong>: uses an atomic {@code INSERT ... ON CONFLICT DO NOTHING}
 * ({@link ProcessedEventJpaRepository#claimEvent}) executed in the SAME transaction as the
 * business dispatch. {@code rows == 1} proceeds; {@code rows == 0} skips a duplicate. Because the
 * claim shares the consumer's transaction, a dispatch failure rolls the marker back too, so a
 * Kafka redelivery re-processes the event instead of permanently losing the cache entry.
 *
 * <p><strong>Poison-message handling (M1)</strong>: required fields ({@code eventId},
 * {@code tenantId}, {@code payload.accountId}, {@code payload.email}) are validated up-front via
 * {@link #requiredText} and {@link #requiredUuid}. A malformed payload throws
 * {@link MalformedEventException} which is caught here — the event is logged and discarded
 * without retrying. The idempotency claim is NOT made for poison messages.
 */
@Component
public class AccountRegisteredConsumer {

    private static final Logger log = LoggerFactory.getLogger(AccountRegisteredConsumer.class);
    private static final String TOPIC = "auth.account.registered";

    private final CacheUserEmailUseCase cacheUserEmailUseCase;
    private final ProcessedEventJpaRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    public AccountRegisteredConsumer(CacheUserEmailUseCase cacheUserEmailUseCase,
            ProcessedEventJpaRepository processedEventRepository,
            ObjectMapper objectMapper) {
        this.cacheUserEmailUseCase = cacheUserEmailUseCase;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = TOPIC, groupId = "notification-group")
    @Transactional
    public void consume(ConsumerRecord<String, String> record) {
        Context traceContext = KafkaTracingSupport.extractTraceContext(record.headers());
        try (Scope ignored = traceContext.makeCurrent()) {
            processRecord(record.value());
        }
    }

    private void processRecord(String message) {
        // ── Up-front parse (M1 poison guard) ─────────────────────────────────
        JsonNode root;
        try {
            root = objectMapper.readTree(message);
        } catch (JsonProcessingException e) {
            log.error("Malformed JSON on topic {} — discarding (poison)", TOPIC, e);
            return;
        }

        String eventId;
        UUID tenantId;
        UUID accountId;
        String email;
        try {
            eventId = requiredText(root, "eventId");
            tenantId = requiredUuid(root, "tenantId");
            JsonNode payload = root.get("payload");
            if (payload == null || payload.isNull()) {
                throw new MalformedEventException("Missing required field: payload");
            }
            accountId = requiredUuid(payload, "accountId");
            email = requiredText(payload, "email");
        } catch (MalformedEventException | IllegalArgumentException e) {
            log.error("Malformed AccountRegistered event on topic {} — discarding (poison): {}",
                    TOPIC, e.getMessage());
            return;
        }

        // ── Atomic idempotency claim (same transaction as dispatch) ────────────
        if (processedEventRepository.claimEvent(eventId, TOPIC, Instant.now()) == 0) {
            log.debug("Skipping duplicate AccountRegistered event: eventId={}", eventId);
            return;
        }

        // ── Business dispatch ──────────────────────────────────────────────────
        try {
            cacheUserEmailUseCase.cacheUserEmail(accountId, tenantId, email);
            log.info("Cached user email from AccountRegistered: eventId={}, accountId={}", eventId, accountId);
        } catch (Exception e) {
            log.error("Failed to cache user email for eventId={}: {}", eventId, e.getMessage(), e);
            throw new RuntimeException("Failed to process AccountRegistered event", e);
        }
    }

    // ── Field helpers (M1 poison guard) ──────────────────────────────────────

    /**
     * Extracts a required non-blank text field from a JSON node.
     *
     * @throws MalformedEventException if the field is absent, null, or blank
     */
    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new MalformedEventException("Missing required field: " + field);
        }
        String text = value.asText(null);
        if (text == null || text.isBlank()) {
            throw new MalformedEventException("Empty required field: " + field);
        }
        return text;
    }

    /**
     * Extracts a required UUID field from a JSON node.
     *
     * @throws MalformedEventException if the field is absent, null, blank, or not a valid UUID
     */
    private static UUID requiredUuid(JsonNode node, String field) {
        String text = requiredText(node, field);
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            throw new MalformedEventException("Invalid UUID for field " + field + ": " + text, e);
        }
    }
}
