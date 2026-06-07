package com.epm.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import com.epm.notification.domain.model.UserEmailCache;
import com.epm.notification.infrastructure.adapter.out.persistence.UserEmailCacheJpaRepository;
import com.epm.notification.infrastructure.adapter.out.persistence.UserEmailCacheRepositoryAdapter;
import com.epm.notification.infrastructure.AbstractPostgresIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration test for UserEmailCacheRepositoryAdapter using real PostgreSQL (TDD — Strict).
 *
 * <p>Uses Testcontainers via AbstractPostgresIT — no external PostgreSQL required.
 * Verifies persistence and upsert-idempotency of the email cache.
 */
@DataJpaTest
@Import({UserEmailCacheRepositoryAdapter.class})
@TestPropertySource(properties = {
    "spring.config.import=",
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "eureka.client.enabled=false"
})
class UserEmailCacheRepositoryAdapterTest extends AbstractPostgresIT {

    @Autowired
    private UserEmailCacheJpaRepository jpaRepository;

    @Autowired
    private UserEmailCacheRepositoryAdapter adapter;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        jpaRepository.deleteAll();
    }

    // ── save() persists new row ────────────────────────────────────────────

    @Test
    void save_persistsNewEntry() {
        UUID userId = UUID.randomUUID();
        UserEmailCache entry = new UserEmailCache(userId, tenantId, "user@example.com", LocalDateTime.now());

        adapter.save(entry);

        assertThat(jpaRepository.count()).isEqualTo(1);
        assertThat(jpaRepository.findById(userId)).isPresent()
                .get()
                .extracting(e -> e.getEmail())
                .isEqualTo("user@example.com");
    }

    // ── findByUserId() returns cached email ───────────────────────────────

    @Test
    void findByUserId_returnsEntryWhenPresent() {
        UUID userId = UUID.randomUUID();
        UserEmailCache entry = new UserEmailCache(userId, tenantId, "found@example.com", LocalDateTime.now());
        adapter.save(entry);

        Optional<UserEmailCache> result = adapter.findByUserId(userId);

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("found@example.com");
        assertThat(result.get().getUserId()).isEqualTo(userId);
        assertThat(result.get().getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void findByUserId_returnsEmptyWhenAbsent() {
        UUID unknownUserId = UUID.randomUUID();

        Optional<UserEmailCache> result = adapter.findByUserId(unknownUserId);

        assertThat(result).isEmpty();
    }

    // ── save() is idempotent / upsert behavior ────────────────────────────

    @Test
    void save_existingUserId_updatesEmail() {
        UUID userId = UUID.randomUUID();
        adapter.save(new UserEmailCache(userId, tenantId, "old@example.com", LocalDateTime.now()));

        adapter.save(new UserEmailCache(userId, tenantId, "new@example.com", LocalDateTime.now()));

        assertThat(jpaRepository.count()).isEqualTo(1);
        assertThat(jpaRepository.findById(userId)).isPresent()
                .get()
                .extracting(e -> e.getEmail())
                .isEqualTo("new@example.com");
    }

    @Test
    void save_duplicateSave_isIdempotent() {
        UUID userId = UUID.randomUUID();
        UserEmailCache entry = new UserEmailCache(userId, tenantId, "same@example.com", LocalDateTime.now());

        adapter.save(entry);
        adapter.save(entry);

        assertThat(jpaRepository.count()).isEqualTo(1);
    }
}
