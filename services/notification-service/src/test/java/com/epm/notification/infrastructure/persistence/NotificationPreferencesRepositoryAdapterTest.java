package com.epm.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.epm.notification.domain.model.NotificationChannel;
import com.epm.notification.domain.model.NotificationPreference;
import com.epm.notification.domain.model.NotificationType;
import com.epm.notification.infrastructure.adapter.out.persistence.NotificationPreferenceJpaRepository;
import com.epm.notification.infrastructure.adapter.out.persistence.NotificationPreferencesRepositoryAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration test for NotificationPreferencesRepositoryAdapter using real PostgreSQL (TDD — Strict).
 *
 * <p>Verifies upsert, find by key, and findAll semantics.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({NotificationPreferencesRepositoryAdapter.class})
@TestPropertySource(properties = {
    "spring.config.import=",
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "eureka.client.enabled=false"
})
class NotificationPreferencesRepositoryAdapterTest {

    @Autowired
    private NotificationPreferenceJpaRepository jpaRepository;

    @Autowired
    private NotificationPreferencesRepositoryAdapter adapter;

    private UUID userId;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        jpaRepository.deleteAll();
    }

    // ── upsert() persists a new row ────────────────────────────────────────

    @Test
    void upsert_persistsNewPreference() {
        NotificationPreference pref = NotificationPreference.create(
                userId, tenantId, NotificationType.TASK_ASSIGNED, NotificationChannel.IN_APP, false);

        adapter.upsert(pref);

        assertThat(jpaRepository.count()).isEqualTo(1);
    }

    // ── findByUserIdAndEventTypeAndChannel() returns persisted preference ──

    @Test
    void findByUserIdAndEventTypeAndChannel_returnsPersistedPreference() {
        NotificationPreference pref = NotificationPreference.create(
                userId, tenantId, NotificationType.PROJECT_CREATED, NotificationChannel.EMAIL, false);
        adapter.upsert(pref);

        Optional<NotificationPreference> result = adapter.findByUserIdAndEventTypeAndChannel(
                userId, NotificationType.PROJECT_CREATED, NotificationChannel.EMAIL);

        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo(userId);
        assertThat(result.get().getEventType()).isEqualTo(NotificationType.PROJECT_CREATED);
        assertThat(result.get().getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(result.get().isEnabled()).isFalse();
    }

    @Test
    void findByUserIdAndEventTypeAndChannel_returnsEmptyWhenAbsent() {
        Optional<NotificationPreference> result = adapter.findByUserIdAndEventTypeAndChannel(
                userId, NotificationType.TASK_ASSIGNED, NotificationChannel.IN_APP);

        assertThat(result).isEmpty();
    }

    // ── upsert() on existing → updates (no duplicate row) ─────────────────

    @Test
    void upsert_onExistingPreference_updatesWithoutCreatingDuplicate() {
        NotificationPreference pref = NotificationPreference.create(
                userId, tenantId, NotificationType.TASK_ASSIGNED, NotificationChannel.IN_APP, true);
        adapter.upsert(pref);

        // upsert with enabled=false
        NotificationPreference updated = NotificationPreference.create(
                userId, tenantId, NotificationType.TASK_ASSIGNED, NotificationChannel.IN_APP, false);
        adapter.upsert(updated);

        assertThat(jpaRepository.count()).isEqualTo(1);
        Optional<NotificationPreference> result = adapter.findByUserIdAndEventTypeAndChannel(
                userId, NotificationType.TASK_ASSIGNED, NotificationChannel.IN_APP);
        assertThat(result).isPresent();
        assertThat(result.get().isEnabled()).isFalse();
    }

    // ── findAllByUserId() returns all preferences for user ─────────────────

    @Test
    void findAllByUserId_returnsAllPreferencesForUser() {
        adapter.upsert(NotificationPreference.create(
                userId, tenantId, NotificationType.TASK_ASSIGNED, NotificationChannel.IN_APP, true));
        adapter.upsert(NotificationPreference.create(
                userId, tenantId, NotificationType.PROJECT_CREATED, NotificationChannel.EMAIL, false));

        // Different user — should not appear
        UUID otherUser = UUID.randomUUID();
        adapter.upsert(NotificationPreference.create(
                otherUser, tenantId, NotificationType.TASK_ASSIGNED, NotificationChannel.EMAIL, true));

        List<NotificationPreference> result = adapter.findAllByUserId(userId);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(p -> p.getUserId().equals(userId));
    }
}
