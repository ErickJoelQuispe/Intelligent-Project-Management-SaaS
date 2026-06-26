package com.epm.user.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;

import com.epm.user.domain.model.UserPreferences;
import com.epm.user.domain.model.UserProfile;
import com.epm.user.infrastructure.AbstractPostgresIT;
import com.epm.user.infrastructure.adapter.out.persistence.UserProfileJpaRepository;
import com.epm.user.infrastructure.adapter.out.persistence.UserProfilePersistenceAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration test for {@link UserProfilePersistenceAdapter} — uses Testcontainers via AbstractPostgresIT.
 *
 * <p>Verifies CRUD operations and tenant isolation for user profiles.
 */
@DataJpaTest
@Import({UserProfilePersistenceAdapter.class, ObjectMapper.class})
@TestPropertySource(properties = {
    "spring.config.import=",
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "eureka.client.enabled=false"
})
class UserProfilePersistenceAdapterIT extends AbstractPostgresIT {

    @Autowired
    private UserProfilePersistenceAdapter adapter;

    @Autowired
    private UserProfileJpaRepository jpaRepository;

    private UUID tenantId;
    private UUID profileId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        profileId = UUID.randomUUID();
        jpaRepository.deleteAll();
    }

    // ── save and findByIdAndTenantId → present ────────────────────────────────

    @Test
    void save_and_findByIdAndTenantId_returnsProfile() {
        UserProfile profile = UserProfile.create(profileId, tenantId, "alice@example.com",
                "Alice", "Smith");

        adapter.save(profile);

        Optional<UserProfile> found = adapter.findByIdAndTenantId(profileId, tenantId);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(profileId);
        assertThat(found.get().getTenantId()).isEqualTo(tenantId);
        assertThat(found.get().getEmail()).isEqualTo("alice@example.com");
        assertThat(found.get().getFirstName()).isEqualTo("Alice");
        assertThat(found.get().getLastName()).isEqualTo("Smith");
    }

    // ── tenant isolation: wrong tenantId → empty ──────────────────────────────

    @Test
    void findByIdAndTenantId_withWrongTenantId_returnsEmpty() {
        UserProfile profile = UserProfile.create(profileId, tenantId, "bob@example.com",
                "Bob", "Jones");
        adapter.save(profile);

        UUID differentTenantId = UUID.randomUUID();

        Optional<UserProfile> found = adapter.findByIdAndTenantId(profileId, differentTenantId);

        assertThat(found).isEmpty();
    }

    // ── update profile fields → persisted ─────────────────────────────────────

    @Test
    void save_updatesExistingProfile_fieldsArePersisted() {
        UserProfile profile = UserProfile.create(profileId, tenantId, "charlie@example.com",
                "Charlie", "Brown");
        UserProfile saved = adapter.save(profile);

        // Update via domain method
        saved.update("Charles", "Brown", "Developer bio", "https://avatar.example.com/c.png", 0L);
        adapter.save(saved);

        Optional<UserProfile> found = adapter.findByIdAndTenantId(profileId, tenantId);

        assertThat(found).isPresent();
        assertThat(found.get().getFirstName()).isEqualTo("Charles");
        assertThat(found.get().getBio()).isEqualTo("Developer bio");
        assertThat(found.get().getAvatarUrl()).isEqualTo("https://avatar.example.com/c.png");
    }
}
