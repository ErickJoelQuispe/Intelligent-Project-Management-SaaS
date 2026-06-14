package com.epm.user.infrastructure.adapter.in.messaging;

import java.util.UUID;

import com.epm.user.domain.model.UserProfile;
import com.epm.user.infrastructure.adapter.out.persistence.UserProfilePersistenceAdapter;
import com.fasterxml.jackson.core.JsonProcessingException;
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
 * <h3>Processing flow</h3>
 * <ol>
 *   <li><strong>Up-front parse → poison (skip):</strong> the envelope is parsed into typed
 *       variables with explicit null/blank checks BEFORE the idempotency claim. A
 *       structurally invalid payload raises a {@link MalformedEventException}; together with
 *       {@link JsonProcessingException} it is treated as a poison message — logged at ERROR
 *       and skipped (no rethrow) so a message that will NEVER succeed does not waste the
 *       error handler's retries or pollute the DLT.</li>
 *   <li><strong>Claim (REQUIRES_NEW):</strong> idempotency is claimed up-front via
 *       {@link IdempotencyGuard#claim}, which runs in its OWN {@code REQUIRES_NEW}
 *       transaction. This both prevents double-processing (the processed_events primary key
 *       is the authoritative lock) and avoids the {@code UnexpectedRollbackException} trap:
 *       when two concurrent deliveries of the same eventId race, the loser's PK violation is
 *       confined to the guard's separate transaction, so the consumer's own
 *       {@code @Transactional} transaction is never marked rollback-only and the loser
 *       returns benignly instead of throwing at commit.</li>
 *   <li><strong>Genuine conflict (distinct log + DLT):</strong> a unique-constraint
 *       violation on {@code user_profiles} (e.g. {@code uix_user_profiles_tenant_email} — a
 *       different eventId but the same tenant+email) is a real data conflict, NOT an
 *       idempotency duplicate. The profile is persisted via {@code saveAndFlush} so the
 *       {@link DataIntegrityViolationException} surfaces synchronously HERE; it is caught,
 *       logged with a CLEAR, distinguishable alert (so it is never confused with a transient
 *       failure), and rethrown so the existing error handler routes it to the DLT for manual
 *       triage. It is deliberately NOT swallowed or auto-reprocessed.</li>
 *   <li><strong>Transient (retry + DLT):</strong> any other error (DB connectivity, a
 *       business {@link NullPointerException}, etc.) falls through to the generic
 *       {@code catch(Exception)} and is rethrown so the error handler retries and eventually
 *       routes to the DLT. A business NPE lands here and is rethrown — it is NOT treated as
 *       poison.</li>
 * </ol>
 */
@Component
public class AccountRegisteredConsumer {

    private static final Logger log = LoggerFactory.getLogger(AccountRegisteredConsumer.class);
    private static final String TOPIC = "auth.account.registered";

    private final UserProfilePersistenceAdapter profileRepository;
    private final IdempotencyGuard idempotencyGuard;
    private final ObjectMapper objectMapper;

    public AccountRegisteredConsumer(UserProfilePersistenceAdapter profileRepository,
            IdempotencyGuard idempotencyGuard,
            ObjectMapper objectMapper) {
        this.profileRepository = profileRepository;
        this.idempotencyGuard = idempotencyGuard;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = TOPIC, groupId = "user-service")
    @Transactional
    public void consume(String message) {
        ParsedAccountRegistered parsed;
        try {
            parsed = parse(message);
        } catch (MalformedEventException | JsonProcessingException e) {
            // Structurally invalid payload — poison message. Log at ERROR and return without
            // rethrowing: a structurally broken message will NEVER succeed, so retrying it 3×
            // and parking it in the DLT only adds noise. The operator inspects the logs to
            // diagnose the bad message.
            log.error("Poison message on topic {} — skipping to prevent retry/DLT noise: {}",
                    TOPIC, e.getMessage(), e);
            return;
        }

        try {
            // Claim the event in a SEPARATE REQUIRES_NEW transaction. A concurrent delivery
            // of the same eventId loses the race and is skipped benignly — without poisoning
            // this consumer's transaction (no UnexpectedRollbackException at commit).
            if (!idempotencyGuard.claim(parsed.eventId(), TOPIC)) {
                log.debug("Duplicate event skipped: {}", parsed.eventId());
                return;
            }

            // Create profile. saveAndFlush forces the INSERT now, so a genuine user_profiles
            // unique-constraint violation (different eventId, same tenant+email) surfaces
            // HERE as a DataIntegrityViolationException rather than escaping at commit.
            UserProfile profile = UserProfile.create(
                    parsed.accountId(), parsed.tenantId(), parsed.email(),
                    parsed.firstName(), parsed.lastName());
            try {
                profileRepository.saveAndFlush(profile);
            } catch (DataIntegrityViolationException ex) {
                // A real data conflict on user_profiles — NOT an idempotency duplicate (that
                // is handled by the REQUIRES_NEW guard on processed_events) and NOT a
                // transient failure. Log a DISTINCT, unambiguous alert and rethrow so the
                // existing DefaultErrorHandler routes it to the DLT for manual triage. We do
                // NOT swallow or auto-reprocess it: a genuine conflict will keep failing on
                // retry and correctly lands in the DLT.
                log.error("DATA CONFLICT on user_profiles (likely duplicate tenant+email) for "
                        + "accountId={} eventId={} — routing to DLT for manual triage, not a "
                        + "transient error", parsed.accountId(), parsed.eventId(), ex);
                throw ex;
            }

            log.info("Created UserProfile for account: {}", parsed.accountId());
        } catch (DataIntegrityViolationException e) {
            // Already logged distinctly above; rethrow so the error handler routes to the DLT.
            throw e;
        } catch (Exception e) {
            // Genuinely transient error (DB connectivity, a business NullPointerException,
            // etc.) — rethrow so the error handler retries and eventually routes to the DLT.
            log.error("Transient failure processing auth.account.registered event, will retry", e);
            throw new RuntimeException("Failed to process auth.account.registered event", e);
        }
    }

    // ── Parsing ────────────────────────────────────────────────────────────────

    /**
     * Parses and validates the envelope into typed fields up-front. Every required field is
     * null-checked explicitly so a missing field becomes a {@link MalformedEventException}
     * (poison) rather than an ambiguous {@link NullPointerException} later. {@code firstName}
     * and {@code lastName} are optional and default to {@code "Unknown"}.
     */
    private ParsedAccountRegistered parse(String message) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(message);

        String eventId = requiredText(root, "eventId");
        JsonNode payload = root.get("payload");
        if (payload == null || payload.isNull()) {
            throw new MalformedEventException("Missing required field 'payload'");
        }
        UUID accountId = requiredUuid(payload, "accountId");
        UUID tenantId = resolveTenantId(root, payload);
        String email = requiredText(payload, "email");
        String firstName = payload.path("firstName").asText("Unknown");
        String lastName = payload.path("lastName").asText("Unknown");

        return new ParsedAccountRegistered(eventId, accountId, tenantId, email, firstName, lastName);
    }

    /**
     * Resolves the tenantId from the payload, falling back to the envelope root. If BOTH are
     * absent or invalid the message is malformed.
     */
    private UUID resolveTenantId(JsonNode root, JsonNode payload) {
        JsonNode payloadTenant = payload.get("tenantId");
        if (payloadTenant != null && !payloadTenant.isNull() && !payloadTenant.asText().isBlank()) {
            return parseUuid("tenantId", payloadTenant.asText());
        }
        JsonNode rootTenant = root.get("tenantId");
        if (rootTenant != null && !rootTenant.isNull() && !rootTenant.asText().isBlank()) {
            return parseUuid("tenantId", rootTenant.asText());
        }
        throw new MalformedEventException("Missing or blank required field 'tenantId'");
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new MalformedEventException("Missing or blank required field '" + field + "'");
        }
        return value.asText();
    }

    private UUID requiredUuid(JsonNode node, String field) {
        return parseUuid(field, requiredText(node, field));
    }

    private UUID parseUuid(String field, String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new MalformedEventException("Field '" + field + "' is not a valid UUID: " + raw, e);
        }
    }

    /** Immutable, fully-validated view of an AccountRegistered envelope. */
    private record ParsedAccountRegistered(String eventId, UUID accountId, UUID tenantId,
            String email, String firstName, String lastName) {
    }
}
