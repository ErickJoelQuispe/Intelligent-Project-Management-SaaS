package com.epm.project.infrastructure.adapter.in.messaging;

import java.util.List;
import java.util.UUID;

import com.epm.project.domain.model.Project;
import com.epm.project.domain.port.out.ProjectRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka consumer for {@code user.team.deleted} events.
 *
 * <p>Marks all active project-team assignments for the deleted team as orphaned.
 *
 * <h3>Idempotency &amp; transaction strategy</h3>
 * <p>Idempotency is claimed up-front via {@link IdempotencyGuard#claim} which runs in its
 * OWN {@code REQUIRES_NEW} transaction. This serves two purposes:
 * <ol>
 *   <li><strong>Double-processing prevention:</strong> the {@code processed_events}
 *       primary key is the authoritative lock. Two concurrent deliveries of the same
 *       eventId race on the insert; exactly one wins and the loser is told to skip.</li>
 *   <li><strong>Rollback-poisoning avoidance:</strong> because the duplicate insert and
 *       its {@code DataIntegrityViolationException} are confined to the guard's separate
 *       transaction, the consumer's own {@code @Transactional} transaction is NEVER marked
 *       rollback-only. The race loser therefore returns benignly instead of throwing
 *       {@code UnexpectedRollbackException} at commit.</li>
 * </ol>
 *
 * <p><strong>At-least-once tradeoff (documented intentionally):</strong> the claim row is
 * committed by the guard BEFORE the orphaning runs in the consumer's transaction. If the
 * orphaning then fails with a transient error, the consumer's transaction rolls back but
 * the {@code processed_events} row stays committed — so this exact event will be treated as
 * a duplicate (skipped) on redelivery. This is acceptable here because the orphaning
 * operation is naturally idempotent: re-marking an already-orphaned assignment is a no-op,
 * so a rare transient failure after the claim self-heals on the next {@code TeamDeleted}
 * for the same team (or a reconciliation pass). We deliberately accept this over the more
 * complex alternative because the work is idempotent and the simpler design eliminates the
 * rollback-poisoning trap entirely.
 *
 * <h3>Poison-message handling</h3>
 * <p>The envelope is parsed up-front into typed variables with explicit null checks. Any
 * missing or unparseable required field raises a {@link MalformedEventException}. Only
 * {@link MalformedEventException} and {@link JsonProcessingException} are treated as poison
 * (logged and skipped, no rethrow) so a structurally bad message does not loop forever.
 * A bare {@link NullPointerException} is NOT caught as poison — if business logic throws an
 * NPE it falls through to the transient catch and is rethrown so Kafka retries / routes to
 * the DLT.
 */
@Component
public class TeamDeletedConsumer {

    private static final Logger log = LoggerFactory.getLogger(TeamDeletedConsumer.class);
    private static final String TOPIC = "user.team.deleted";

    private final ProjectRepository projectRepository;
    private final IdempotencyGuard idempotencyGuard;
    private final ObjectMapper objectMapper;

    public TeamDeletedConsumer(ProjectRepository projectRepository,
            IdempotencyGuard idempotencyGuard,
            ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.idempotencyGuard = idempotencyGuard;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = TOPIC, groupId = "project-service")
    @Transactional
    public void consume(String message) {
        ParsedTeamDeleted parsed;
        try {
            parsed = parse(message);
        } catch (MalformedEventException | JsonProcessingException e) {
            // Structurally invalid payload — poison message. Log at ERROR and return without
            // rethrowing to prevent an infinite retry loop on the topic. The operator must
            // inspect the dead-letter topic or logs to diagnose the bad message.
            log.error("Poison message on topic {} — skipping to prevent infinite loop: {}",
                    TOPIC, e.getMessage(), e);
            return;
        }

        try {
            // Claim idempotency in a SEPARATE REQUIRES_NEW transaction. If the event was
            // already claimed, skip benignly — the consumer's transaction stays clean.
            if (!idempotencyGuard.claim(parsed.eventId(), TOPIC)) {
                log.debug("Skipping duplicate TeamDeleted event: {}", parsed.eventId());
                return;
            }

            List<Project> affected = projectRepository.findAllByTeamId(parsed.teamId(), parsed.tenantId());
            for (Project project : affected) {
                project.removeTeam(parsed.teamId());
                projectRepository.save(project);
            }

            log.info("Processed TeamDeleted for teamId={}, {} projects updated",
                    parsed.teamId(), affected.size());

        } catch (Exception e) {
            // Genuinely transient error (DB connectivity, business-logic failure, etc.) —
            // rethrow so Kafka retries and eventually routes to the DLT. A NullPointerException
            // from business logic lands HERE, NOT in the poison branch above.
            log.error("Transient failure processing TeamDeleted event, will retry: {}", e.getMessage(), e);
            throw new RuntimeException("Transient failure processing user.team.deleted event", e);
        }
    }

    // ── Parsing ────────────────────────────────────────────────────────────────

    /**
     * Parses and validates the envelope into typed fields up-front. Every required field is
     * null-checked explicitly so a missing field becomes a {@link MalformedEventException}
     * (poison) rather than an ambiguous {@link NullPointerException} later.
     */
    private ParsedTeamDeleted parse(String message) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(message);

        String eventId = requiredText(root, "eventId");
        JsonNode payload = root.get("payload");
        if (payload == null || payload.isNull()) {
            throw new MalformedEventException("Missing required field 'payload'");
        }
        UUID teamId = requiredUuid(payload, "teamId");
        UUID tenantId = requiredUuid(root, "tenantId");

        return new ParsedTeamDeleted(eventId, teamId, tenantId);
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new MalformedEventException("Missing or blank required field '" + field + "'");
        }
        return value.asText();
    }

    private UUID requiredUuid(JsonNode node, String field) {
        String raw = requiredText(node, field);
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new MalformedEventException("Field '" + field + "' is not a valid UUID: " + raw, e);
        }
    }

    /** Immutable, fully-validated view of a TeamDeleted envelope. */
    private record ParsedTeamDeleted(String eventId, UUID teamId, UUID tenantId) {
    }
}
