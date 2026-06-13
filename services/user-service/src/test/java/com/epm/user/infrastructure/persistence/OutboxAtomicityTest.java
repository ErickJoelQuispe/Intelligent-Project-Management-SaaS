package com.epm.user.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.util.UUID;

import com.epm.user.domain.model.Team;
import com.epm.user.domain.port.out.DomainEventPublisher;
import com.epm.user.domain.port.out.TransactionalOutboxWriter;
import com.epm.user.infrastructure.AbstractPostgresIT;
import com.epm.user.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import com.epm.user.infrastructure.adapter.out.persistence.TeamJpaRepository;
import com.epm.user.infrastructure.adapter.out.persistence.TeamMembershipJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration test proving outbox atomicity: if the DomainEventPublisher throws after
 * a successful aggregate save, BOTH the aggregate row and the outbox row must be absent
 * after the transaction rolls back.
 *
 * <p>Uses {@code @SpringBootTest} (NON-transactional by default) with a real
 * Testcontainers PostgreSQL database so that the {@code @Transactional} boundary on
 * {@link TransactionalOutboxWriter#saveTeamAndPublish} creates and rolls back a real
 * database transaction — not just an in-test mock.
 *
 * <p>The {@link DomainEventPublisher} is replaced with a Mockito mock that throws on
 * first call, simulating an outbox write failure.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.config.import=",
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "eureka.client.enabled=false",
    "spring.kafka.bootstrap-servers=localhost:9092",
    "spring.security.oauth2.resourceserver.jwt.jwks-uri=https://example.com/.well-known/jwks.json"
})
class OutboxAtomicityTest extends AbstractPostgresIT {

    @Autowired
    private TransactionalOutboxWriter outboxWriter;

    @Autowired
    private TeamJpaRepository teamJpaRepository;

    @Autowired
    private TeamMembershipJpaRepository membershipJpaRepository;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @MockitoBean
    private DomainEventPublisher domainEventPublisher;

    @AfterEach
    void cleanup() {
        membershipJpaRepository.deleteAll();
        teamJpaRepository.deleteAll();
        outboxEventJpaRepository.deleteAll();
    }

    /**
     * Proves that when the DomainEventPublisher throws after a successful team save,
     * the entire transaction rolls back: no team row and no outbox row remain in the DB.
     *
     * <p>This is the real rollback proof — we read state from a FRESH transaction
     * after the failed saveTeamAndPublish call completes.
     */
    @Test
    void saveTeamAndPublish_rollsBackBothTeamAndOutboxOnPublisherFailure() {
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Team team = Team.create(tenantId, ownerId, "Atomic Test Team", null);

        // Simulate publisher failure after the aggregate save
        doThrow(new RuntimeException("Simulated publisher failure for atomicity test"))
                .when(domainEventPublisher).publish(any());

        // The saveTeamAndPublish call must propagate the publisher exception
        assertThatThrownBy(() -> outboxWriter.saveTeamAndPublish(team))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated publisher failure");

        // Fresh read (new transaction) — both rows must be absent after rollback
        assertThat(teamJpaRepository.findAll())
                .as("Team row must not exist after transaction rollback")
                .isEmpty();
        assertThat(outboxEventJpaRepository.findAll())
                .as("Outbox row must not exist after transaction rollback")
                .isEmpty();
    }
}
