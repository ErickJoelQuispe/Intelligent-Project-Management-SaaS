package com.epm.task.infrastructure.adapter.out.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.epm.task.domain.event.ProjectTasksCancelled;
import com.epm.task.domain.event.TaskAssigned;
import com.epm.task.domain.event.TaskCreated;
import com.epm.task.domain.event.TaskDeleted;
import com.epm.task.domain.event.TaskStatusChanged;
import com.epm.task.domain.event.TaskUpdated;
import com.epm.task.domain.port.out.DomainEventPublisher;
import com.epm.task.infrastructure.adapter.out.persistence.OutboxEventJpaEntity;
import com.epm.task.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Implements {@link DomainEventPublisher} using the outbox pattern for task-service.
 *
 * <p>Handles TaskCreated, TaskUpdated, TaskStatusChanged, TaskAssigned, TaskDeleted,
 * and {@link ProjectTasksCancelled} (aggregate event emitted by the project-archive cascade).
 * All events are published to the {@code task.events} topic.
 */
@Component
public class OutboxDomainEventPublisher implements DomainEventPublisher {

    static final String TOPIC_TASK_EVENTS = "task.events";

    private final OutboxEventJpaRepository outboxRepo;
    private final ApplicationEventPublisher appEventPublisher;
    private final ObjectMapper objectMapper;

    public OutboxDomainEventPublisher(OutboxEventJpaRepository outboxRepo,
            ApplicationEventPublisher appEventPublisher,
            ObjectMapper objectMapper) {
        this.outboxRepo = outboxRepo;
        this.appEventPublisher = appEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(List<Object> events) {
        for (Object event : events) {
            OutboxEventJpaEntity entity = null;

            if (event instanceof TaskCreated e) {
                entity = buildEntity(e.taskId(), "Task", "TaskCreated",
                        TOPIC_TASK_EVENTS, buildTaskCreatedPayload(e));
            } else if (event instanceof TaskUpdated e) {
                entity = buildEntity(e.taskId(), "Task", "TaskUpdated",
                        TOPIC_TASK_EVENTS, buildTaskUpdatedPayload(e));
            } else if (event instanceof TaskStatusChanged e) {
                entity = buildEntity(e.taskId(), "Task", "TaskStatusChanged",
                        TOPIC_TASK_EVENTS, buildTaskStatusChangedPayload(e));
            } else if (event instanceof TaskAssigned e) {
                entity = buildEntity(e.taskId(), "Task", "TaskAssigned",
                        TOPIC_TASK_EVENTS, buildTaskAssignedPayload(e));
            } else if (event instanceof TaskDeleted e) {
                entity = buildEntity(e.taskId(), "Task", "TaskDeleted",
                        TOPIC_TASK_EVENTS, buildTaskDeletedPayload(e));
            } else if (event instanceof ProjectTasksCancelled e) {
                entity = buildEntity(e.projectId(), "Project", "ProjectTasksCancelled",
                        TOPIC_TASK_EVENTS, buildProjectTasksCancelledPayload(e));
            }

            if (entity != null) {
                outboxRepo.save(entity);
                appEventPublisher.publishEvent(new OutboxEventSavedEvent(this));
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private OutboxEventJpaEntity buildEntity(UUID aggregateId, String aggregateType,
            String eventType, String topic, String payload) {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
        entity.setId(UuidCreator.getTimeOrderedEpoch());
        entity.setAggregateId(aggregateId);
        entity.setAggregateType(aggregateType);
        entity.setEventType(eventType);
        entity.setTopic(topic);
        entity.setPayload(payload);
        entity.setCreatedAt(Instant.now());
        return entity;
    }

    private String buildTaskCreatedPayload(TaskCreated event) {
        try {
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", event.eventId().toString());
            envelope.put("eventType", "TaskCreated");
            envelope.put("eventVersion", 1);
            envelope.put("tenantId", event.tenantId().toString());
            envelope.put("occurredAt", event.occurredAt().toString());
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("taskId", event.taskId().toString());
            payload.put("projectId", event.projectId().toString());
            payload.put("title", event.title());
            if (event.actorId() != null) {
                payload.put("actorId", event.actorId().toString());
            }
            envelope.set("payload", payload);
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize TaskCreated event", e);
        }
    }

    private String buildTaskUpdatedPayload(TaskUpdated event) {
        try {
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", event.eventId().toString());
            envelope.put("eventType", "TaskUpdated");
            envelope.put("eventVersion", 1);
            envelope.put("tenantId", event.tenantId().toString());
            envelope.put("occurredAt", event.occurredAt().toString());
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("taskId", event.taskId().toString());
            envelope.set("payload", payload);
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize TaskUpdated event", e);
        }
    }

    private String buildTaskStatusChangedPayload(TaskStatusChanged event) {
        try {
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", event.eventId().toString());
            envelope.put("eventType", "TaskStatusChanged");
            envelope.put("eventVersion", 1);
            envelope.put("tenantId", event.tenantId().toString());
            envelope.put("occurredAt", event.occurredAt().toString());
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("taskId", event.taskId().toString());
            payload.put("oldStatus", event.oldStatus().name());
            payload.put("newStatus", event.newStatus().name());
            envelope.set("payload", payload);
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize TaskStatusChanged event", e);
        }
    }

    private String buildTaskAssignedPayload(TaskAssigned event) {
        try {
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", event.eventId().toString());
            envelope.put("eventType", "TaskAssigned");
            envelope.put("eventVersion", 1);
            envelope.put("tenantId", event.tenantId().toString());
            envelope.put("occurredAt", event.occurredAt().toString());
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("taskId", event.taskId().toString());
            if (event.assigneeId() != null) {
                payload.put("assigneeId", event.assigneeId().toString());
            }
            envelope.set("payload", payload);
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize TaskAssigned event", e);
        }
    }

    private String buildTaskDeletedPayload(TaskDeleted event) {
        try {
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", event.eventId().toString());
            envelope.put("eventType", "TaskDeleted");
            envelope.put("eventVersion", 1);
            envelope.put("tenantId", event.tenantId().toString());
            envelope.put("occurredAt", event.occurredAt().toString());
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("taskId", event.taskId().toString());
            payload.put("projectId", event.projectId().toString());
            envelope.set("payload", payload);
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize TaskDeleted event", e);
        }
    }

    private String buildProjectTasksCancelledPayload(ProjectTasksCancelled event) {
        try {
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", event.eventId().toString());
            envelope.put("eventType", "ProjectTasksCancelled");
            envelope.put("eventVersion", 1);
            envelope.put("tenantId", event.tenantId().toString());
            envelope.put("occurredAt", event.occurredAt().toString());
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("projectId", event.projectId().toString());
            payload.put("cancelledCount", event.cancelledCount());
            envelope.set("payload", payload);
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize ProjectTasksCancelled event", e);
        }
    }
}
