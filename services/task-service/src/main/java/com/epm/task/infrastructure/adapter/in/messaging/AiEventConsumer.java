package com.epm.task.infrastructure.adapter.in.messaging;

import java.time.Instant;
import java.util.UUID;

import com.epm.task.domain.model.TaskPriority;
import com.epm.task.domain.port.in.CreateTaskUseCase;
import com.epm.task.domain.port.in.command.CreateTaskCommand;
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
 * Kafka consumer for {@code ai.events} topic — {@code AiTasksGenerated} event type.
 *
 * <p>For each event, creates one {@link com.epm.task.domain.model.Task} per draft in the batch
 * using {@link CreateTaskUseCase}. The entire batch is wrapped in a single transaction.
 * Uses {@code processed_events} for idempotency — duplicate events are silently skipped.
 * Malformed JSON is logged and discarded without crashing the consumer.
 */
@Component
public class AiEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AiEventConsumer.class);
    private static final String TOPIC = "ai.events";

    private final CreateTaskUseCase createTaskUseCase;
    private final ProcessedEventJpaRepository processedEventRepo;
    private final ObjectMapper objectMapper;

    public AiEventConsumer(CreateTaskUseCase createTaskUseCase,
            ProcessedEventJpaRepository processedEventRepo) {
        this.createTaskUseCase = createTaskUseCase;
        this.processedEventRepo = processedEventRepo;
        this.objectMapper = new ObjectMapper();
    }

    @KafkaListener(topics = TOPIC, groupId = "task-service")
    @Transactional
    public void consume(String message) {
        JsonNode root;
        try {
            root = objectMapper.readTree(message);
        } catch (Exception e) {
            log.error("Malformed JSON received on ai.events — discarding message", e);
            return;
        }

        try {
            String eventId = root.get("eventId").asText();

            // Idempotency check — skip if already processed
            if (processedEventRepo.existsByEventId(eventId)) {
                log.debug("Skipping duplicate AiTasksGenerated event: {}", eventId);
                return;
            }

            JsonNode payload = root.get("payload");
            UUID projectId = UUID.fromString(payload.get("projectId").asText());
            UUID tenantId = UUID.fromString(root.get("tenantId").asText());
            UUID generatedBy = UUID.fromString(payload.get("generatedBy").asText());

            JsonNode tasks = payload.get("tasks");
            for (JsonNode taskDraft : tasks) {
                String title = taskDraft.get("title").asText();
                String description = taskDraft.has("description")
                        ? taskDraft.get("description").asText()
                        : null;
                TaskPriority priority = TaskPriority.valueOf(
                        taskDraft.get("priority").asText().toUpperCase());

                CreateTaskCommand command = new CreateTaskCommand(
                        tenantId,
                        projectId,
                        generatedBy,
                        title,
                        description,
                        priority,
                        null,
                        null);

                createTaskUseCase.execute(command);
            }

            processedEventRepo.save(new ProcessedEventJpaEntity(eventId, TOPIC, Instant.now()));
            log.info("Processed AiTasksGenerated eventId={} — {} tasks created from AI batch",
                    eventId, tasks.size());

        } catch (Exception e) {
            log.error("Failed to process AiTasksGenerated event — rolling back transaction", e);
            throw new RuntimeException("Failed to process AiTasksGenerated event", e);
        }
    }
}
