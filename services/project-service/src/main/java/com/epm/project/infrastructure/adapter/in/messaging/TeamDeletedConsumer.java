package com.epm.project.infrastructure.adapter.in.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.epm.project.domain.model.Project;
import com.epm.project.domain.port.out.ProjectRepository;
import com.epm.project.infrastructure.adapter.out.persistence.ProcessedEventJpaEntity;
import com.epm.project.infrastructure.adapter.out.persistence.ProcessedEventJpaRepository;
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
 * Kafka consumer for {@code user.team.deleted} events.
 *
 * <p>Marks all active project-team assignments for the deleted team as orphaned.
 * Uses {@code processed_events} for idempotency with an insert-first strategy (FIX 4):
 * instead of check-then-act (TOCTOU race), we attempt to insert the idempotency
 * row first via {@code saveAndFlush} and catch {@link DataIntegrityViolationException}
 * as a benign duplicate signal.
 *
 * <p><strong>Error-handling policy (FIX 17):</strong>
 * <ul>
 *   <li>Benign duplicate ({@link DataIntegrityViolationException} on processed_events PK):
 *       log at DEBUG and return normally — ack without processing.</li>
 *   <li>Malformed/unparseable payload ({@link JsonProcessingException},
 *       {@link NullPointerException} from missing JSON fields): log at ERROR and return
 *       normally — skip the poison message so it does not infinite-loop on the topic.</li>
 *   <li>Transient infrastructure errors (DB connectivity, etc.): rethrow so Kafka
 *       retries and eventually routes to the DLT.</li>
 * </ul>
 *
 * <p>{@code @Transactional} ensures that if the business orphaning logic or a later
 * project save fails after the idempotency row is inserted, the whole transaction rolls
 * back — allowing a legitimate retry on the next delivery.
 */
@Component
public class TeamDeletedConsumer {

    private static final Logger log = LoggerFactory.getLogger(TeamDeletedConsumer.class);
    private static final String TOPIC = "user.team.deleted";

    private final ProjectRepository projectRepository;
    private final ProcessedEventJpaRepository processedEventRepo;
    private final ObjectMapper objectMapper;

    public TeamDeletedConsumer(ProjectRepository projectRepository,
            ProcessedEventJpaRepository processedEventRepo,
            ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.processedEventRepo = processedEventRepo;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = TOPIC, groupId = "project-service")
    @Transactional
    public void consume(String message) {
        String eventId = null;
        try {
            JsonNode root = objectMapper.readTree(message);
            eventId = root.get("eventId").asText();

            // Fast-path: skip without a DB round-trip if already processed.
            // The authoritative duplicate claim is the saveAndFlush below.
            if (processedEventRepo.existsByEventId(eventId)) {
                log.debug("Skipping duplicate TeamDeleted event: {}", eventId);
                return;
            }

            // Insert-first idempotency — authoritative backstop against TOCTOU race.
            // Two concurrent threads with the same eventId will race on this insert;
            // the loser catches DataIntegrityViolationException and returns benignly.
            // @Transactional keeps the idempotency row and any subsequent writes atomic:
            // if business logic fails AFTER this point, the whole tx rolls back and the
            // message can be legitimately retried.
            processedEventRepo.saveAndFlush(new ProcessedEventJpaEntity(eventId, TOPIC, Instant.now()));

            UUID teamId = UUID.fromString(root.get("payload").get("teamId").asText());
            UUID tenantId = UUID.fromString(root.get("tenantId").asText());

            List<Project> affected = projectRepository.findAllByTeamId(teamId, tenantId);
            for (Project project : affected) {
                project.removeTeam(teamId);
                projectRepository.save(project);
            }

            log.info("Processed TeamDeleted for teamId={}, {} projects updated", teamId, affected.size());

        } catch (DataIntegrityViolationException e) {
            // Benign duplicate: the processed_events PK constraint fired, meaning another
            // thread/consumer already claimed this eventId. Ack and return normally — do NOT rethrow.
            log.debug("Benign duplicate TeamDeleted event (concurrent insert race): eventId={}", eventId);

        } catch (JsonProcessingException | NullPointerException e) {
            // Malformed or unparseable payload — poison message. Logging at ERROR and returning
            // without rethrowing prevents an infinite retry loop on the topic. The operator
            // must inspect the dead-letter topic or logs to diagnose the bad message.
            log.error("Poison message on topic {} — skipping to prevent infinite loop: {}",
                    TOPIC, e.getMessage(), e);

        } catch (Exception e) {
            // Genuinely transient error (DB connectivity, etc.) — rethrow so Kafka retries
            // and eventually routes to the DLT. Do NOT swallow these.
            log.error("Transient failure processing TeamDeleted event, will retry: {}", e.getMessage(), e);
            throw new RuntimeException("Transient failure processing user.team.deleted event", e);
        }
    }
}
