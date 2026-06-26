package com.epm.user.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.epm.user.domain.model.UserPreferences;
import com.epm.user.domain.model.UserProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for preferences round-trip serialization in {@link UserProfilePersistenceAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class UserProfilePersistenceAdapterTest {

    @Mock
    private UserProfileJpaRepository jpaRepository;

    private UserProfilePersistenceAdapter adapter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        adapter = new UserProfilePersistenceAdapter(jpaRepository, objectMapper);
    }

    // ── preferences round-trip: serialize → deserialize → same values ─────────

    @Test
    void saveThenFindPreservesPreferences() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(userId, tenantId, "alice@example.com", "Alice", "Smith");
        UserPreferences prefs = new UserPreferences("es", "America/New_York", "DD/MM/YYYY", "SUNDAY");
        profile.update("Alice", "Smith", null, null, 0, prefs);

        UserProfileJpaEntity entity = adapter.toEntityForTest(profile);

        assertThat(entity.getPreferencesJson()).isNotEmpty();
        UserProfile reconstituted = adapter.toDomainForTest(entity);

        assertThat(reconstituted.getPreferences().language()).isEqualTo("es");
        assertThat(reconstituted.getPreferences().timezone()).isEqualTo("America/New_York");
        assertThat(reconstituted.getPreferences().dateFormat()).isEqualTo("DD/MM/YYYY");
        assertThat(reconstituted.getPreferences().startOfWeek()).isEqualTo("SUNDAY");
    }

    // ── TRIANGULATE: null preferencesJson → defaults ──────────────────────────

    @Test
    void nullPreferencesJsonDeserialisesToDefaults() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(userId, tenantId, "bob@example.com", "Bob", "Jones");

        UserProfileJpaEntity entity = adapter.toEntityForTest(profile);
        // Simulate a legacy row with no preferences JSON
        entity.setPreferencesJson(null);

        UserProfile reconstituted = adapter.toDomainForTest(entity);

        UserPreferences defaults = UserPreferences.defaults();
        assertThat(reconstituted.getPreferences().language()).isEqualTo(defaults.language());
        assertThat(reconstituted.getPreferences().timezone()).isEqualTo(defaults.timezone());
    }

    // ── TRIANGULATE: empty string preferencesJson → defaults ──────────────────

    @Test
    void emptyPreferencesJsonDeserialisesToDefaults() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(userId, tenantId, "carol@example.com", "Carol", "White");

        UserProfileJpaEntity entity = adapter.toEntityForTest(profile);
        entity.setPreferencesJson("");

        UserProfile reconstituted = adapter.toDomainForTest(entity);

        assertThat(reconstituted.getPreferences()).isEqualTo(UserPreferences.defaults());
    }
}
