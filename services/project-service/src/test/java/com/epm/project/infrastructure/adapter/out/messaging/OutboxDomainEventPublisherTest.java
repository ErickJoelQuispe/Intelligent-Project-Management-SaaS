package com.epm.project.infrastructure.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.epm.project.domain.event.ProjectArchived;
import com.epm.project.domain.event.ProjectCreated;
import com.epm.project.domain.event.ProjectUpdated;
import com.epm.project.domain.event.TeamAssignedToProject;
import com.epm.project.infrastructure.adapter.out.persistence.OutboxEventJpaEntity;
import com.epm.project.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Unit tests for {@link OutboxDomainEventPublisher}.
 *
 * <p>Verifies correct topic name and key payload fields for each of the four
 * project domain events.
 */
@ExtendWith(MockitoExtension.class)
class OutboxDomainEventPublisherTest {

    @Mock
    private OutboxEventJpaRepository outboxRepo;

    @Mock
    private ApplicationEventPublisher appEventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── ProjectCreated ────────────────────────────────────────────────────────

    @Test
    void publish_projectCreated_usesCorrectTopicAndPayload() throws Exception {
        OutboxDomainEventPublisher publisher =
                new OutboxDomainEventPublisher(outboxRepo, appEventPublisher, objectMapper);

        UUID projectId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        ProjectCreated event = new ProjectCreated(UUID.randomUUID(), projectId, "My Project",
                ownerId, tenantId, Instant.now());

        publisher.publish(List.of(event));

        ArgumentCaptor<OutboxEventJpaEntity> captor = forClass(OutboxEventJpaEntity.class);
        verify(outboxRepo).save(captor.capture());
        OutboxEventJpaEntity saved = captor.getValue();

        assertThat(saved.getTopic()).isEqualTo("project.project.created");
        assertThat(saved.getEventType()).isEqualTo("ProjectCreated");
        assertThat(saved.getAggregateId()).isEqualTo(projectId);

        JsonNode payload = objectMapper.readTree(saved.getPayload());
        assertThat(payload.get("eventType").asText()).isEqualTo("ProjectCreated");
        assertThat(payload.get("payload").get("projectId").asText()).isEqualTo(projectId.toString());
        assertThat(payload.get("payload").get("ownerId").asText()).isEqualTo(ownerId.toString());
    }

    // ── ProjectUpdated ────────────────────────────────────────────────────────

    @Test
    void publish_projectUpdated_usesCorrectTopicAndPayload() throws Exception {
        OutboxDomainEventPublisher publisher =
                new OutboxDomainEventPublisher(outboxRepo, appEventPublisher, objectMapper);

        UUID projectId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ProjectUpdated event = new ProjectUpdated(UUID.randomUUID(), projectId,
                "Updated Name", "New desc", tenantId, Instant.now());

        publisher.publish(List.of(event));

        ArgumentCaptor<OutboxEventJpaEntity> captor = forClass(OutboxEventJpaEntity.class);
        verify(outboxRepo).save(captor.capture());
        OutboxEventJpaEntity saved = captor.getValue();

        assertThat(saved.getTopic()).isEqualTo("project.project.updated");
        assertThat(saved.getEventType()).isEqualTo("ProjectUpdated");

        JsonNode payload = objectMapper.readTree(saved.getPayload());
        assertThat(payload.get("payload").get("name").asText()).isEqualTo("Updated Name");
    }

    // ── ProjectArchived ───────────────────────────────────────────────────────

    @Test
    void publish_projectArchived_usesCorrectTopicAndPayload() throws Exception {
        OutboxDomainEventPublisher publisher =
                new OutboxDomainEventPublisher(outboxRepo, appEventPublisher, objectMapper);

        UUID projectId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ProjectArchived event = new ProjectArchived(UUID.randomUUID(), projectId, tenantId, Instant.now());

        publisher.publish(List.of(event));

        ArgumentCaptor<OutboxEventJpaEntity> captor = forClass(OutboxEventJpaEntity.class);
        verify(outboxRepo).save(captor.capture());
        OutboxEventJpaEntity saved = captor.getValue();

        assertThat(saved.getTopic()).isEqualTo("project.project.archived");
        assertThat(saved.getEventType()).isEqualTo("ProjectArchived");

        JsonNode payload = objectMapper.readTree(saved.getPayload());
        assertThat(payload.get("payload").get("projectId").asText()).isEqualTo(projectId.toString());
    }

    // ── TeamAssignedToProject ─────────────────────────────────────────────────

    @Test
    void publish_teamAssignedToProject_usesCorrectTopicAndPayload() throws Exception {
        OutboxDomainEventPublisher publisher =
                new OutboxDomainEventPublisher(outboxRepo, appEventPublisher, objectMapper);

        UUID projectId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        TeamAssignedToProject event = new TeamAssignedToProject(UUID.randomUUID(),
                projectId, teamId, tenantId, Instant.now());

        publisher.publish(List.of(event));

        ArgumentCaptor<OutboxEventJpaEntity> captor = forClass(OutboxEventJpaEntity.class);
        verify(outboxRepo).save(captor.capture());
        OutboxEventJpaEntity saved = captor.getValue();

        assertThat(saved.getTopic()).isEqualTo("project.team.assigned");
        assertThat(saved.getEventType()).isEqualTo("TeamAssignedToProject");

        JsonNode payload = objectMapper.readTree(saved.getPayload());
        assertThat(payload.get("payload").get("teamId").asText()).isEqualTo(teamId.toString());
        assertThat(payload.get("payload").get("projectId").asText()).isEqualTo(projectId.toString());
    }
}
