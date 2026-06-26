package com.epm.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import com.epm.user.domain.event.ProfileUpdated;
import com.epm.user.domain.exception.OptimisticLockException;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link UserProfile} aggregate root.
 *
 * <p>Tests run RED first — UserProfile class does not exist yet.
 */
class UserProfileTest {

    @Test
    void createSetsIdToProvidedAccountId() {
        UUID accountId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(accountId, tenantId, "alice@example.com", "Alice", "Smith");
        assertThat(profile.getId()).isEqualTo(accountId);
    }

    @Test
    void createSetsTenantIdCorrectly() {
        UUID accountId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(accountId, tenantId, "alice@example.com", "Alice", "Smith");
        assertThat(profile.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void createStoresNormalizedEmail() {
        UUID accountId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(accountId, tenantId, "ALICE@EXAMPLE.COM", "Alice", "Smith");
        assertThat(profile.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void updateChangesFirstNameLastNameBioAndAvatarUrl() {
        UserProfile profile = buildProfile();
        profile.update("Bob", "Jones", "Bio text", "http://avatar.com/img.png", 0);
        assertThat(profile.getFirstName()).isEqualTo("Bob");
        assertThat(profile.getLastName()).isEqualTo("Jones");
        assertThat(profile.getBio()).isEqualTo("Bio text");
        assertThat(profile.getAvatarUrl()).isEqualTo("http://avatar.com/img.png");
    }

    @Test
    void updateDoesNotChangeEmail() {
        UserProfile profile = buildProfile();
        String originalEmail = profile.getEmail();
        profile.update("Bob", "Jones", null, null, 0);
        assertThat(profile.getEmail()).isEqualTo(originalEmail);
    }

    @Test
    void updateIncrementsVersion() {
        UserProfile profile = buildProfile();
        assertThat(profile.getVersion()).isEqualTo(0);
        profile.update("Bob", "Jones", null, null, 0);
        assertThat(profile.getVersion()).isEqualTo(1);
    }

    @Test
    void updateRecordsProfileUpdatedDomainEvent() {
        UserProfile profile = buildProfile();
        profile.update("Bob", "Jones", "bio", "avatar", 0);
        List<Object> events = profile.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(ProfileUpdated.class);
    }

    @Test
    void updateWithWrongVersionThrowsOptimisticLockException() {
        UserProfile profile = buildProfile();
        // version is 0, but we send version=1 (wrong)
        assertThatThrownBy(() -> profile.update("Bob", "Jones", null, null, 1))
                .isInstanceOf(OptimisticLockException.class);
    }

    @Test
    void pullDomainEventsClearsEvents() {
        UserProfile profile = buildProfile();
        profile.update("Bob", "Jones", null, null, 0);
        profile.pullDomainEvents(); // first pull
        List<Object> secondPull = profile.pullDomainEvents();
        assertThat(secondPull).isEmpty();
    }

    // ── getPreferences(): defaults when null ──────────────────────────────────

    @Test
    void getPreferencesReturnsDefaultsForNewProfile() {
        UserProfile profile = buildProfile();
        UserPreferences prefs = profile.getPreferences();
        assertThat(prefs.language()).isEqualTo("en");
        assertThat(prefs.timezone()).isEqualTo("UTC");
        assertThat(prefs.dateFormat()).isEqualTo("ISO");
        assertThat(prefs.startOfWeek()).isEqualTo("MONDAY");
    }

    @Test
    void updateWithPreferencesSetsPreferencesOnProfile() {
        UserProfile profile = buildProfile();
        UserPreferences prefs = new UserPreferences("es", "America/New_York", "DD/MM/YYYY", "SUNDAY");
        profile.update("Alice", "Smith", null, null, 0, prefs);
        assertThat(profile.getPreferences().language()).isEqualTo("es");
        assertThat(profile.getPreferences().timezone()).isEqualTo("America/New_York");
    }

    @Test
    void updateWithNullPreferencesKeepsExistingPreferences() {
        UserProfile profile = buildProfile();
        UserPreferences initial = new UserPreferences("pt", "UTC", "MM/DD/YYYY", "SATURDAY");
        profile.update("Alice", "Smith", null, null, 0, initial);
        // second update with null preferences should not clear them
        profile.update("Alice", "Smith", null, null, 1, null);
        assertThat(profile.getPreferences().language()).isEqualTo("pt");
    }

    // ── softDelete() ──────────────────────────────────────────────────────────

    @Test
    void softDeleteSetsDeletedAt() {
        UserProfile profile = buildProfile();
        assertThat(profile.getDeletedAt()).isNull();
        profile.softDelete();
        assertThat(profile.getDeletedAt()).isNotNull();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private UserProfile buildProfile() {
        return UserProfile.create(UUID.randomUUID(), UUID.randomUUID(), "alice@example.com", "Alice", "Smith");
    }
}
