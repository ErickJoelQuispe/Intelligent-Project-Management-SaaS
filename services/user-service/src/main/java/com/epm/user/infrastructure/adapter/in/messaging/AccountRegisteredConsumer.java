package com.epm.user.infrastructure.adapter.in.messaging;

import java.time.Instant;

import com.epm.user.domain.model.UserProfile;
import com.epm.user.infrastructure.adapter.out.persistence.ProcessedEventJpaEntity;
import com.epm.user.infrastructure.adapter.out.persistence.ProcessedEventJpaRepository;
import com.epm.user.infrastructure.adapter.out.persistence.UserProfilePersistenceAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka consumer for {@code auth.account.registered} events.
 *
 * <p>Creates a {@link UserProfile} when an account is registered in auth-service.
 * Uses the processed_events table for idempotency.
 *
 * <h3>Idempotency strategy (FIX 1 — TOCTOU-safe)</h3>
 * <ol>
 *   <li>Fast-path: {@code existsByEventId} short-circuits the common case where the
 *       event was already processed in a prior delivery.</li>
 *   <li>Race backstop: {@code processedEventRepository.save} is wrapped in a
 *       {@code try/catch(DataIntegrityViolationException)}. If two concurrent deliveries
 *       of the same eventId both pass the fast-path check simultaneously, the second
 *       thread will hit the processed_events primary key constraint and get a
 *       {@link DataIntegrityViolationException} instead of proceeding to create a
 *       duplicate profile. The catch returns silently — the message is acked without
 *       going to the DLT.</li>
 * </ol>
 *
 * <p>Because the entire method is {@code @Transactional}, a failure in the profile
 * save rolls back the processed_events insert too, allowing a legitimate retry.
 *
 * <h3>Genuine conflicts are NOT benign</h3>
 * <p>The {@code DataIntegrityViolationException} catch is scoped <em>only</em> to the
 * processed_events claim. A unique-constraint violation on {@code user_profiles}
 * (e.g. {@code uix_user_profiles_tenant_email} — a different eventId but the same
 * tenant+email) is a real data conflict, not an idempotency duplicate. It must NOT
 * be silently acked: it is allowed to propagate so the generic {@code catch(Exception)}
 * rethrows and the message is routed to the DLT for investigation. To make that
 * violation surface synchronously inside this method (rather than escaping at commit
 * as a poison-pill on the transaction that already holds the processed_events row),
 * the profile is persisted via {@code saveAndFlush}.
 */
@Component
public class AccountRegisteredConsumer {

    private static final Logger log = LoggerFactory.getLogger(AccountRegisteredConsumer.class);
    private static final String TOPIC = "auth.account.registered";

    private final UserProfilePersistenceAdapter profileRepository;
    private final ProcessedEventJpaRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    public AccountRegisteredConsumer(UserProfilePersistenceAdapter profileRepository,
            ProcessedEventJpaRepository processedEventRepository,
            ObjectMapper objectMapper) {
        this.profileRepository = profileRepository;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = TOPIC, groupId = "user-service")
    @Transactional
    public void consume(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventId = root.get("eventId").asText();

            // Fast-path idempotency check (common case: already processed in a prior delivery)
            if (processedEventRepository.existsByEventId(eventId)) {
                log.debug("Duplicate event skipped (fast-path): {}", eventId);
                return;
            }

            // Extract payload
            JsonNode payload = root.get("payload");
            String accountIdStr = payload.get("accountId").asText();
            String tenantIdStr = payload.path("tenantId").asText(root.get("tenantId").asText());
            String email = payload.get("email").asText();
            String firstName = payload.path("firstName").asText("Unknown");
            String lastName = payload.path("lastName").asText("Unknown");

            java.util.UUID accountId = java.util.UUID.fromString(accountIdStr);
            java.util.UUID tenantId = java.util.UUID.fromString(tenantIdStr);

            // Attempt to claim the processed_events row. A concurrent thread that also
            // passed the fast-path check will hit the PK constraint here and be caught
            // below — preventing a duplicate profile and avoiding DLT routing.
            try {
                processedEventRepository.saveAndFlush(
                        new ProcessedEventJpaEntity(eventId, TOPIC, Instant.now()));
            } catch (DataIntegrityViolationException duplicate) {
                // Another thread (or a retry on a live transaction) already claimed this
                // eventId. Treat as a benign duplicate: ack the message, do not proceed.
                log.debug("Duplicate event skipped (concurrent race resolved): {}", eventId);
                return;
            }

            // Create profile. saveAndFlush forces the INSERT now, so a genuine
            // user_profiles unique-constraint violation (different eventId, same
            // tenant+email) surfaces HERE as a DataIntegrityViolationException rather
            // than escaping at commit. There is intentionally NO catch for that
            // exception around this call: a real data conflict is NOT a benign
            // idempotency duplicate. It propagates to the generic catch below, which
            // rethrows so the message is routed to the DLT for investigation. If this
            // fails the transaction rolls back including the processed_events row, so a
            // legitimate transient failure can still be retried.
            UserProfile profile = UserProfile.create(accountId, tenantId, email, firstName, lastName);
            profileRepository.saveAndFlush(profile);

            log.info("Created UserProfile for account: {}", accountId);
        } catch (Exception e) {
            log.error("Failed to process auth.account.registered event", e);
            throw new RuntimeException("Failed to process auth.account.registered event", e);
        }
    }
}
