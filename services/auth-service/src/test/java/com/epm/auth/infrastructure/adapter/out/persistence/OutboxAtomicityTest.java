package com.epm.auth.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import java.util.UUID;

import com.epm.auth.domain.model.Account;
import com.epm.auth.domain.port.out.DomainEventPublisher;
import com.epm.auth.infrastructure.AbstractPostgresIT;
import com.epm.auth.infrastructure.adapter.out.identity.KeycloakAdminAdapter;
import com.epm.auth.infrastructure.adapter.out.messaging.KafkaOutboxPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Real atomicity test for {@link TransactionalAccountRegistration}.
 *
 * <p>Unlike a {@code @DataJpaTest} slice — which wraps every test in a single auto-rollback
 * transaction that {@code saveAndPublish} merely joins (so the negative case passes even WITHOUT
 * {@code @Transactional}) — this test runs under {@code @SpringBootTest} and is NOT itself
 * transactional. Each call to {@code saveAndPublish} starts its OWN transaction (the bean is
 * {@code @Transactional}, invoked from a non-transactional test) which commits or rolls back
 * independently. We then perform FRESH reads (each repository call is its own transaction) to
 * assert what was actually committed to the real Postgres container.
 *
 * <p>Positive case: the publisher stub inserts an outbox row; after commit both the account row
 * and the outbox row are visible from a fresh read.
 *
 * <p>Negative case (the one that proves atomicity): the publisher throws; the transaction rolls
 * back, and a fresh read shows NEITHER the account row NOR any outbox row — proving the account
 * insert was rolled back together with the failed outbox insert.
 *
 * <p>Kafka and Keycloak collaborators are mocked via {@link MockitoBean} so the full context
 * starts without external brokers. Only the JPA persistence path is exercised.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    "spring.config.import=",
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "eureka.client.enabled=false",
    "keycloak.server-url=http://localhost:8180",
    "keycloak.realm=epm",
    "keycloak.client-id=epm-backend",
    "keycloak.client-secret=test-secret"
})
class OutboxAtomicityTest extends AbstractPostgresIT {

    @MockitoBean
    private DomainEventPublisher domainEventPublisher;

    @MockitoBean
    private KafkaOutboxPublisher kafkaOutboxPublisher;

    @MockitoBean
    private KeycloakAdminAdapter keycloakAdminAdapter;

    @Autowired
    private TransactionalAccountRegistration transactionalRegistration;

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @AfterEach
    void cleanUp() {
        // The Postgres container is real and persists rows across tests within the class.
        // Wipe both tables so each test starts from a known-empty state.
        outboxEventJpaRepository.deleteAll();
        accountJpaRepository.deleteAll();
    }

    @Test
    void saveAndPublishCommitsBothAccountAndOutboxRow() {
        UUID tenantId = UUID.randomUUID();
        Account account = Account.register(tenantId, "atomic-test@example.com", "Atomic", "Test");
        account.linkKeycloakUser(UUID.randomUUID());
        UUID accountId = account.getId();

        // Publisher stub: insert a real outbox row, simulating what the real publisher does.
        doAnswer(inv -> {
            OutboxEventJpaEntity outboxEntity = new OutboxEventJpaEntity();
            outboxEntity.setId(UUID.randomUUID());
            outboxEntity.setAggregateId(accountId);
            outboxEntity.setAggregateType("Account");
            outboxEntity.setEventType("AccountRegistered");
            outboxEntity.setTopic("auth.account.registered");
            outboxEntity.setPayload("{\"test\":true}");
            outboxEntity.setCreatedAt(java.time.Instant.now());
            outboxEventJpaRepository.save(outboxEntity);
            return null;
        }).when(domainEventPublisher).publish(anyList());

        // Act: runs in its own transaction and COMMITS.
        transactionalRegistration.saveAndPublish(account);

        // Assert from a FRESH read (new transaction): account row is committed.
        assertThat(accountJpaRepository.findById(accountId)).isPresent();

        // Assert from a FRESH read: the outbox row was committed alongside the account.
        long outboxCount = outboxEventJpaRepository.findAll().stream()
                .filter(e -> accountId.equals(e.getAggregateId()))
                .count();
        assertThat(outboxCount).isEqualTo(1);
    }

    @Test
    void saveAndPublishRollsBackAccountWhenOutboxInsertFails() {
        UUID tenantId = UUID.randomUUID();
        Account account = Account.register(tenantId, "rollback-test@example.com", "Rollback", "Test");
        account.linkKeycloakUser(UUID.randomUUID());
        UUID accountId = account.getId();

        // Publisher stub: throw to simulate an outbox insert failure.
        doThrow(new RuntimeException("Simulated outbox insert failure"))
                .when(domainEventPublisher).publish(anyList());

        // Act: the exception propagates and marks the transaction for rollback.
        assertThatThrownBy(() -> transactionalRegistration.saveAndPublish(account))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated outbox insert failure");

        // Assert from a FRESH read: the account insert was rolled back together with the
        // failed outbox insert — nothing was committed. THIS is what proves atomicity.
        assertThat(accountJpaRepository.findById(accountId)).isEmpty();

        long outboxCount = outboxEventJpaRepository.findAll().stream()
                .filter(e -> accountId.equals(e.getAggregateId()))
                .count();
        assertThat(outboxCount).isZero();
    }
}
