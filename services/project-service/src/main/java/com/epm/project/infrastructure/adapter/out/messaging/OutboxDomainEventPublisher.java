package com.epm.project.infrastructure.adapter.out.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.epm.project.domain.event.ProjectArchived;
import com.epm.project.domain.event.ProjectCreated;
import com.epm.project.domain.event.ProjectUpdated;
import com.epm.project.domain.event.TeamAssignedToProject;
import com.epm.project.domain.port.out.DomainEventPublisher;
import com.epm.project.infrastructure.adapter.out.persistence.OutboxEventJpaEntity;
import com.epm.project.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Implements {@link DomainEventPublisher} using the outbox pattern for project-service.
 *
 * <p>Handles ProjectCreated, ProjectUpdated, ProjectArchived, and TeamAssignedToProject.
 */
@Component
public class OutboxDomainEventPublisher implements DomainEventPublisher {

    private static final String TOPIC_PROJECT_CREATED = "project.project.created";
    private static final String TOPIC_PROJECT_UPDATED = "project.project.updated";
    private static final String TOPIC_PROJECT_ARCHIVED = "project.project.archived";
    private static final String TOPIC_TEAM_ASSIGNED = "project.team.assigned";

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
            if (event instanceof ProjectCreated e) {
                entity = buildEntity(e.projectId(), "Project", "ProjectCreated",
                        TOPIC_PROJECT_CREATED, buildProjectCreatedPayload(e));
            } else if (event instanceof ProjectUpdated e) {
                entity = buildEntity(e.projectId(), "Project", "ProjectUpdated",
                        TOPIC_PROJECT_UPDATED, buildProjectUpdatedPayload(e));
            } else if (event instanceof ProjectArchived e) {
                entity = buildEntity(e.projectId(), "Project", "ProjectArchived",
                        TOPIC_PROJECT_ARCHIVED, buildProjectArchivedPayload(e));
            } else if (event instanceof TeamAssignedToProject e) {
                entity = buildEntity(e.projectId(), "Project", "TeamAssignedToProject",
                        TOPIC_TEAM_ASSIGNED, buildTeamAssignedPayload(e));
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

    private String buildProjectCreatedPayload(ProjectCreated event) {
        try {
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", event.eventId().toString());
            envelope.put("eventType", "ProjectCreated");
            envelope.put("tenantId", event.tenantId().toString());
            envelope.put("occurredAt", event.occurredAt().toString());
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("projectId", event.projectId().toString());
            payload.put("name", event.name());
            payload.put("ownerId", event.ownerId().toString());
            envelope.set("payload", payload);
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize ProjectCreated event", e);
        }
    }

    private String buildProjectUpdatedPayload(ProjectUpdated event) {
        try {
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", event.eventId().toString());
            envelope.put("eventType", "ProjectUpdated");
            envelope.put("tenantId", event.tenantId().toString());
            envelope.put("occurredAt", event.occurredAt().toString());
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("projectId", event.projectId().toString());
            payload.put("name", event.name());
            if (event.description() != null) {
                payload.put("description", event.description());
            }
            envelope.set("payload", payload);
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize ProjectUpdated event", e);
        }
    }

    private String buildProjectArchivedPayload(ProjectArchived event) {
        try {
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", event.eventId().toString());
            envelope.put("eventType", "ProjectArchived");
            envelope.put("tenantId", event.tenantId().toString());
            envelope.put("occurredAt", event.occurredAt().toString());
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("projectId", event.projectId().toString());
            payload.put("name", event.name());
            payload.put("ownerId", event.ownerId().toString());
            envelope.set("payload", payload);
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize ProjectArchived event", e);
        }
    }

    private String buildTeamAssignedPayload(TeamAssignedToProject event) {
        try {
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", event.eventId().toString());
            envelope.put("eventType", "TeamAssignedToProject");
            envelope.put("tenantId", event.tenantId().toString());
            envelope.put("occurredAt", event.occurredAt().toString());
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("projectId", event.projectId().toString());
            payload.put("teamId", event.teamId().toString());
            var memberIdsNode = objectMapper.createArrayNode();
            for (UUID memberId : event.memberIds()) {
                memberIdsNode.add(memberId.toString());
            }
            payload.set("memberIds", memberIdsNode);
            envelope.set("payload", payload);
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize TeamAssignedToProject event", e);
        }
    }
}
