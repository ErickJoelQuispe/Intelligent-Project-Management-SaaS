package com.epm.task.infrastructure.adapter.in.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.epm.task.domain.model.Task;
import com.epm.task.domain.port.out.ActivityLogRepository;
import com.epm.task.domain.port.out.DomainEventPublisher;
import com.epm.task.domain.port.out.TaskRepository;
import com.epm.task.infrastructure.adapter.out.persistence.ProcessedEventJpaEntity;
import com.epm.task.infrastructure.adapter.out.persistence.ProcessedEventJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka consumer for {@code project.project.archived} events.
 *
 * <p>Cancels all tasks in the archived project. Uses {@code processed_events}
 * for idempotency — duplicate events are skipped.
 */
@Component
public class ProjectArchivedConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProjectArchivedConsumer.class);
    private static final String TOPIC = "project.project.archived";

    private final TaskRepository taskRepository;
    private final ActivityLogRepository activityLogRepository;
    private final DomainEventPublisher eventPublisher;
    private final ProcessedEventJpaRepository processedEventRepo;
    private final ObjectMapper objectMapper;

    public ProjectArchivedConsumer(TaskRepository taskRepository,
            ActivityLogRepository activityLogRepository,
            DomainEventPublisher eventPublisher,
            ProcessedEventJpaRepository processedEventRepo,
            ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
        this.activityLogRepository = activityLogRepository;
        this.eventPublisher = eventPublisher;
        this.processedEventRepo = processedEventRepo;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = TOPIC, groupId = "task-service-project-archived")
    @Transactional
    public void consume(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventId = root.get("eventId").asText();

            // Idempotency check
            if (processedEventRepo.existsByEventId(eventId)) {
                log.debug("Skipping duplicate ProjectArchived event: {}", eventId);
                return;
            }

            UUID projectId = UUID.fromString(root.get("payload").get("projectId").asText());
            UUID tenantId = UUID.fromString(root.get("tenantId").asText());

            List<Task> tasks = taskRepository.findAllByProjectId(projectId, tenantId);
            for (Task task : tasks) {
                if (task.getStatus() != com.epm.task.domain.model.TaskStatus.CANCELLED) {
                    task.cancel();
                    taskRepository.save(task);
                    eventPublisher.publish(task.pullDomainEvents());
                }
            }

            processedEventRepo.save(new ProcessedEventJpaEntity(eventId, TOPIC, Instant.now()));
            log.info("Processed ProjectArchived for projectId={}, {} tasks cancelled",
                    projectId, tasks.size());
        } catch (Exception e) {
            log.error("Failed to process project.project.archived event", e);
            throw new RuntimeException("Failed to process project.project.archived event", e);
        }
    }
}
