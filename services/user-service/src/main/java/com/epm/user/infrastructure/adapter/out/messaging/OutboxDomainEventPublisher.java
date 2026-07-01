package com.epm.user.infrastructure.adapter.out.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.epm.user.domain.event.InvitationCreatedEvent;
import com.epm.user.domain.event.ProfileUpdated;
import com.epm.user.domain.event.TeamCreated;
import com.epm.user.domain.event.TeamDeleted;
import com.epm.user.domain.event.TeamMemberJoined;
import com.epm.user.domain.event.TeamMemberLeft;
import com.epm.user.domain.port.out.DomainEventPublisher;
import com.epm.user.infrastructure.adapter.out.persistence.OutboxEventJpaEntity;
import com.epm.user.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Implements {@link DomainEventPublisher} port using the outbox pattern for user-service.
 *
 * <p>Handles ProfileUpdated, TeamCreated, TeamDeleted, TeamMemberJoined, and TeamMemberLeft domain events.
 */
@Component
public class OutboxDomainEventPublisher implements DomainEventPublisher {

    private static final String TOPIC_PROFILE_UPDATED = "user.profile.updated";
    private static final String TOPIC_TEAM_CREATED = "user.team.created";
    private static final String TOPIC_TEAM_DELETED = "user.team.deleted";
    private static final String TOPIC_TEAM_MEMBER_JOINED = "user.team.member-joined";
    private static final String TOPIC_TEAM_MEMBER_LEFT = "user.team.member-left";
    private static final String TOPIC_INVITATION_SENT = "user.invitation.sent";

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
            if (event instanceof ProfileUpdated e) {
                entity = buildEntity(e.userId(), "UserProfile", "ProfileUpdated",
                        TOPIC_PROFILE_UPDATED, buildProfileUpdatedPayload(e));
            } else if (event instanceof TeamCreated e) {
                entity = buildEntity(e.teamId(), "Team", "TeamCreated",
                        TOPIC_TEAM_CREATED, buildTeamCreatedPayload(e));
            } else if (event instanceof TeamDeleted e) {
                entity = buildEntity(e.teamId(), "Team", "TeamDeleted",
                        TOPIC_TEAM_DELETED, buildTeamDeletedPayload(e));
            } else if (event instanceof TeamMemberJoined e) {
                entity = buildEntity(e.teamId(), "Team", "TeamMemberJoined",
                        TOPIC_TEAM_MEMBER_JOINED, buildTeamMemberJoinedPayload(e));
            } else if (event instanceof TeamMemberLeft e) {
                entity = buildEntity(e.teamId(), "Team", "TeamMemberLeft",
                        TOPIC_TEAM_MEMBER_LEFT, buildTeamMemberLeftPayload(e));
            } else if (event instanceof InvitationCreatedEvent e) {
                entity = buildEntity(e.invitationId(), "Invitation", "InvitationCreated",
                        TOPIC_INVITATION_SENT, buildInvitationCreatedPayload(e));
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

    private String buildProfileUpdatedPayload(ProfileUpdated event) {
        try {
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", event.eventId().toString());
            envelope.put("eventType", "ProfileUpdated");
            envelope.put("eventVersion", 1);
            envelope.put("tenantId", event.tenantId().toString());
            envelope.put("occurredAt", event.occurredAt().toString());
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("userId", event.userId().toString());
            payload.put("email", event.email());
            payload.put("firstName", event.firstName());
            payload.put("lastName", event.lastName());
            if (event.bio() != null) payload.put("bio", event.bio());
            if (event.avatarUrl() != null) payload.put("avatarUrl", event.avatarUrl());
            envelope.set("payload", payload);
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize ProfileUpdated event", e);
        }
    }

    private String buildTeamCreatedPayload(TeamCreated event) {
        try {
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", event.eventId().toString());
            envelope.put("eventType", "TeamCreated");
            envelope.put("eventVersion", 1);
            envelope.put("tenantId", event.tenantId().toString());
            envelope.put("occurredAt", event.occurredAt().toString());
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("teamId", event.teamId().toString());
            payload.put("ownerId", event.ownerId().toString());
            payload.put("name", event.name());
            envelope.set("payload", payload);
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize TeamCreated event", e);
        }
    }

    private String buildTeamDeletedPayload(TeamDeleted event) {
        try {
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", event.eventId().toString());
            envelope.put("eventType", "TeamDeleted");
            envelope.put("eventVersion", 1);
            envelope.put("tenantId", event.tenantId().toString());
            envelope.put("occurredAt", event.occurredAt().toString());
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("teamId", event.teamId().toString());
            envelope.set("payload", payload);
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize TeamDeleted event", e);
        }
    }

    private String buildTeamMemberJoinedPayload(TeamMemberJoined event) {
        try {
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", event.eventId().toString());
            envelope.put("eventType", "TeamMemberJoined");
            envelope.put("eventVersion", 1);
            envelope.put("tenantId", event.tenantId().toString());
            envelope.put("occurredAt", event.occurredAt().toString());
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("teamId", event.teamId().toString());
            payload.put("userId", event.userId().toString());
            payload.put("role", event.role().name());
            payload.put("teamName", event.teamName());
            if (event.email() != null) payload.put("email", event.email());
            envelope.set("payload", payload);
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize TeamMemberJoined event", e);
        }
    }

    private String buildTeamMemberLeftPayload(TeamMemberLeft event) {
        try {
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", event.eventId().toString());
            envelope.put("eventType", "TeamMemberLeft");
            envelope.put("eventVersion", 1);
            envelope.put("tenantId", event.tenantId().toString());
            envelope.put("occurredAt", event.occurredAt().toString());
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("teamId", event.teamId().toString());
            payload.put("userId", event.userId().toString());
            payload.put("teamName", event.teamName());
            envelope.set("payload", payload);
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize TeamMemberLeft event", e);
        }
    }

    private String buildInvitationCreatedPayload(InvitationCreatedEvent event) {
        try {
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", event.eventId().toString());
            envelope.put("eventType", "InvitationCreated");
            envelope.put("eventVersion", 1);
            envelope.put("tenantId", event.tenantId().toString());
            envelope.put("occurredAt", event.occurredAt().toString());
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("invitationId", event.invitationId().toString());
            payload.put("teamId", event.teamId().toString());
            payload.put("email", event.email());
            payload.put("token", event.token());
            payload.put("role", event.role());
            payload.put("expiresAt", event.expiresAt().toString());
            envelope.set("payload", payload);
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize InvitationCreatedEvent", e);
        }
    }
}
