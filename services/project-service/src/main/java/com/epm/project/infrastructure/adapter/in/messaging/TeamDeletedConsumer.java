package com.epm.project.infrastructure.adapter.in.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.epm.project.domain.model.Project;
import com.epm.project.domain.port.out.ProjectRepository;
import com.epm.project.infrastructure.adapter.out.persistence.ProcessedEventJpaEntity;
import com.epm.project.infrastructure.adapter.out.persistence.ProcessedEventJpaRepository;
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
 * Uses {@code processed_events} for idempotency.
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
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventId = root.get("eventId").asText();

            // Idempotency check
            if (processedEventRepo.existsByEventId(eventId)) {
                log.debug("Skipping duplicate TeamDeleted event: {}", eventId);
                return;
            }

            UUID teamId = UUID.fromString(root.get("payload").get("teamId").asText());
            UUID tenantId = UUID.fromString(root.get("tenantId").asText());

            List<Project> affected = projectRepository.findAllByTeamId(teamId, tenantId);
            for (Project project : affected) {
                project.removeTeam(teamId);
                projectRepository.save(project);
            }

            processedEventRepo.save(new ProcessedEventJpaEntity(eventId, TOPIC, Instant.now()));
            log.info("Processed TeamDeleted for teamId={}, {} projects updated", teamId, affected.size());
        } catch (Exception e) {
            log.error("Failed to process user.team.deleted event", e);
            throw new RuntimeException("Failed to process user.team.deleted event", e);
        }
    }
}
