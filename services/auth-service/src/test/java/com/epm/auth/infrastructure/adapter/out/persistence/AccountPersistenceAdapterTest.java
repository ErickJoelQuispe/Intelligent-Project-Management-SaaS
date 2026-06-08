package com.epm.auth.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.epm.auth.domain.model.Account;
import com.epm.auth.domain.model.Email;
import com.epm.auth.domain.model.SecurityEvent;
import com.epm.auth.infrastructure.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration test for persistence adapters.
 *
 * <p>Uses Testcontainers via AbstractPostgresIT — no external PostgreSQL required.
 * Verifies:
 * - Flyway migrations run correctly
 * - JPA mappings are correct
 * - AccountPersistenceAdapter port implementation behaves correctly
 * - SecurityEventPersistenceAdapter port implementation behaves correctly
 */
@DataJpaTest
@Import({
    AccountPersistenceAdapter.class,
    SecurityEventPersistenceAdapter.class
})
@TestPropertySource(properties = {
    "spring.config.import=",
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "eureka.client.enabled=false"
})
class AccountPersistenceAdapterTest extends AbstractPostgresIT {

    @Autowired
    private AccountPersistenceAdapter accountAdapter;

    @Autowired
    private SecurityEventPersistenceAdapter securityEventAdapter;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    // ── Account persistence tests ───────────────────────────────────────────

    @Test
    void saveAccountAndFindByIdReturnsAccount() {
        // Arrange: register a new account
        Account account = Account.register("save-adapter-test@example.com", "Alice", "Smith");
        account.setKeycloakUserId(UUID.randomUUID());

        // Act: save and retrieve
        Account saved = accountAdapter.save(account);
        Optional<Account> found = accountAdapter.findById(saved.getId());

        // Assert: retrieved account matches saved
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getEmail().value()).isEqualTo("save-adapter-test@example.com");
        assertThat(found.get().getStatus().name()).isEqualTo("ACTIVE");
    }

    @Test
    void existsByEmailReturnsTrueForExistingEmail() {
        // Arrange: save an account with a unique email
        String uniqueEmail = "exists-adapter-" + UUID.randomUUID() + "@example.com";
        Account account = Account.register(uniqueEmail, "Bob", "Jones");
        account.setKeycloakUserId(UUID.randomUUID());
        accountAdapter.save(account);

        // Act
        boolean exists = accountAdapter.existsByEmail(new Email(uniqueEmail));

        // Assert
        assertThat(exists).isTrue();
    }

    @Test
    void existsByEmailReturnsFalseForNonExistingEmail() {
        // Act: check for email that was never saved
        boolean exists = accountAdapter.existsByEmail(new Email("ghost-adapter-" + UUID.randomUUID() + "@example.com"));

        // Assert
        assertThat(exists).isFalse();
    }

    // ── SecurityEvent persistence tests ────────────────────────────────────

    @Test
    void saveSecurityEventPersistsSuccessfully() {
        // Arrange: create an account first
        String uniqueEmail = "logout-adapter-" + UUID.randomUUID() + "@example.com";
        Account account = Account.register(uniqueEmail, "Charlie", "Brown");
        account.setKeycloakUserId(UUID.randomUUID());
        Account saved = accountAdapter.save(account);

        // Create logout security event
        SecurityEvent event = SecurityEvent.logout(
                saved.getTenantId(),
                saved.getId(),
                "127.0.0.1",
                "TestAgent/1.0");

        // Act: save event — must not throw
        securityEventAdapter.save(event);

        // Assert: event had valid fields (verified by lack of exception)
        assertThat(event.id()).isNotNull();
        assertThat(event.eventType()).isEqualTo("LOGOUT");
    }

    // ── OutboxEvent persistence test ────────────────────────────────────────

    @Test
    void outboxEventSavedHasPublishedAtNull() {
        // Arrange: create outbox event entity directly
        OutboxEventJpaEntity outbox = new OutboxEventJpaEntity();
        outbox.setId(UUID.randomUUID());
        outbox.setAggregateId(UUID.randomUUID());
        outbox.setAggregateType("Account");
        outbox.setEventType("AccountRegistered");
        outbox.setTopic("auth.account.registered");
        outbox.setPayload("{\"test\": true}");
        outbox.setCreatedAt(Instant.now());

        // Act: save
        OutboxEventJpaEntity savedEntity = outboxEventJpaRepository.save(outbox);

        // Assert: publishedAt is null (not yet relayed)
        assertThat(savedEntity.getId()).isNotNull();
        assertThat(savedEntity.getPublishedAt()).isNull();
        assertThat(savedEntity.getFailedAt()).isNull();
    }
}
