package com.epm.project.infrastructure.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.awaitility.Awaitility;

import com.epm.project.infrastructure.adapter.out.persistence.ProcessedEventJpaRepository;
import com.epm.project.infrastructure.adapter.out.persistence.ProjectJpaRepository;
import com.epm.project.infrastructure.adapter.out.persistence.ProjectMemberJpaRepository;
import com.epm.project.infrastructure.adapter.out.persistence.ProjectTeamJpaRepository;
import com.epm.project.infrastructure.adapter.out.persistence.ProjectJpaEntity;
import com.epm.project.infrastructure.adapter.out.persistence.ProjectMemberJpaEntity;
import com.epm.project.infrastructure.adapter.out.persistence.ProjectTeamJpaEntity;
import org.apache.kafka.clients.producer.ProducerRecord;
import com.epm.project.infrastructure.AbstractPostgresIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration test for {@link TeamDeletedConsumer}.
 *
 * <p>Uses @EmbeddedKafka to send real Kafka messages and verify the consumer processes them.
 * DB is provided by Testcontainers via AbstractPostgresIT (@ServiceConnection).
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"user.team.deleted"})
@TestPropertySource(properties = {
    "spring.config.import=",
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "eureka.client.enabled=false",
    "spring.security.oauth2.resourceserver.jwt.jwks-uri=https://example.com/.well-known/jwks.json",
    "spring.kafka.consumer.auto-offset-reset=earliest"
})
class TeamDeletedConsumerTest extends AbstractPostgresIT {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ProjectJpaRepository projectRepo;

    @Autowired
    private ProjectTeamJpaRepository teamRepo;

    @Autowired
    private ProjectMemberJpaRepository memberRepo;

    @Autowired
    private ProcessedEventJpaRepository processedEventRepo;

    private UUID tenantId;
    private UUID teamId;
    private UUID projectId;

    @AfterEach
    void cleanup() {
        processedEventRepo.deleteAll();
        teamRepo.deleteAll();
        memberRepo.deleteAll();
        projectRepo.deleteAll();
    }

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        teamId = UUID.randomUUID();
        projectId = UUID.randomUUID();

        // Create a project
        ProjectJpaEntity project = new ProjectJpaEntity();
        project.setId(projectId);
        project.setTenantId(tenantId);
        project.setOwnerId(UUID.randomUUID());
        project.setName("Test Project");
        project.setStatus("ACTIVE");
        project.setVisibility("PRIVATE");
        project.setCreatedAt(Instant.now());
        project.setUpdatedAt(Instant.now());
        project.setCreatedBy("system");
        project.setUpdatedBy("system");
        projectRepo.save(project);

        // Assign the team
        ProjectTeamJpaEntity teamAssignment = new ProjectTeamJpaEntity();
        teamAssignment.setId(UUID.randomUUID());
        teamAssignment.setProjectId(projectId);
        teamAssignment.setTeamId(teamId);
        teamAssignment.setTenantId(tenantId);
        teamAssignment.setAssignedAt(Instant.now());
        teamAssignment.setCreatedAt(Instant.now());
        teamAssignment.setUpdatedAt(Instant.now());
        teamAssignment.setCreatedBy("system");
        teamAssignment.setUpdatedBy("system");
        teamRepo.save(teamAssignment);

        // Add owner member
        ProjectMemberJpaEntity member = new ProjectMemberJpaEntity();
        member.setId(UUID.randomUUID());
        member.setProjectId(projectId);
        member.setProfileId(UUID.randomUUID());
        member.setRole("OWNER");
        member.setTenantId(tenantId);
        member.setJoinedAt(Instant.now());
        member.setCreatedAt(Instant.now());
        member.setUpdatedAt(Instant.now());
        member.setCreatedBy("system");
        member.setUpdatedBy("system");
        memberRepo.save(member);
    }

    // ── Scenario 1: team deleted → orphanedAt set ─────────────────────────────

    @Test
    void teamDeleted_orphansActiveTeamAssignment() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String message = buildTeamDeletedMessage(eventId, teamId, tenantId);

        kafkaTemplate.send(new ProducerRecord<>("user.team.deleted", teamId.toString(), message));

        // Wait until consumer processes the event (orphanedAt set) — 30s for CI
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> teamRepo.findByTeamIdAndOrphanedAtIsNull(teamId).isEmpty());

        var orphaned = teamRepo.findAll().stream()
                .filter(t -> t.getTeamId().equals(teamId))
                .toList();
        assertThat(orphaned).isNotEmpty();
        assertThat(orphaned.get(0).getOrphanedAt()).isNotNull();
    }

    // ── Scenario 2: idempotency — duplicate event is skipped ──────────────────

    @Test
    void teamDeleted_idempotent_duplicateEventSkipped() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String message = buildTeamDeletedMessage(eventId, teamId, tenantId);

        // Send once
        kafkaTemplate.send(new ProducerRecord<>("user.team.deleted", teamId.toString(), message));
        Thread.sleep(3000);

        // Verify first processing recorded
        assertThat(processedEventRepo.existsByEventId(eventId)).isTrue();

        // Record orphanedAt state
        var afterFirst = teamRepo.findAll().stream()
                .filter(t -> t.getTeamId().equals(teamId))
                .findFirst().orElseThrow();
        Instant firstOrphanedAt = afterFirst.getOrphanedAt();

        // Send again (same eventId)
        kafkaTemplate.send(new ProducerRecord<>("user.team.deleted", teamId.toString(), message));
        Thread.sleep(2000);

        // orphanedAt must NOT change
        var afterSecond = teamRepo.findAll().stream()
                .filter(t -> t.getTeamId().equals(teamId))
                .findFirst().orElseThrow();
        assertThat(afterSecond.getOrphanedAt()).isEqualTo(firstOrphanedAt);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String buildTeamDeletedMessage(String eventId, UUID teamId, UUID tenantId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "TeamDeleted",
                  "eventVersion": 1,
                  "tenantId": "%s",
                  "occurredAt": "%s",
                  "payload": {
                    "teamId": "%s"
                  }
                }
                """.formatted(eventId, tenantId, Instant.now(), teamId);
    }
}
