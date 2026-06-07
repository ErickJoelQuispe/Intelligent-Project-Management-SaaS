package com.epm.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.epm.notification.domain.model.Notification;
import com.epm.notification.domain.model.NotificationType;
import com.epm.notification.infrastructure.adapter.out.persistence.NotificationJpaEntity;
import com.epm.notification.infrastructure.adapter.out.persistence.NotificationJpaRepository;
import com.epm.notification.infrastructure.adapter.out.persistence.NotificationRepositoryAdapter;
import com.epm.notification.infrastructure.AbstractPostgresIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration test for NotificationRepositoryAdapter using real PostgreSQL (T-C-04).
 *
 * <p>Uses Testcontainers via AbstractPostgresIT — no external PostgreSQL required.
 */
@DataJpaTest
@Import({NotificationRepositoryAdapter.class})
@TestPropertySource(properties = {
    "spring.config.import=",
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "eureka.client.enabled=false"
})
class NotificationRepositoryAdapterTest extends AbstractPostgresIT {

    @Autowired
    private NotificationJpaRepository jpaRepository;

    @Autowired
    private NotificationRepositoryAdapter adapter;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        jpaRepository.deleteAll();
    }

    // ── T-C-04: save() persists notification ──────────────────────────────

    @Test
    void save_persistsNotificationAndCanBeFoundById() {
        Notification notification = Notification.create(tenantId, userId,
                NotificationType.TASK_ASSIGNED, UUID.randomUUID(), "Task assigned to you");

        Notification saved = adapter.save(notification);

        assertThat(saved.getId()).isEqualTo(notification.getId());
        assertThat(jpaRepository.count()).isEqualTo(1);
        Optional<NotificationJpaEntity> entity = jpaRepository.findById(saved.getId());
        assertThat(entity).isPresent();
        assertThat(entity.get().getType()).isEqualTo(NotificationType.TASK_ASSIGNED);
    }

    @Test
    void save_withReadTrue_persistsReadState() {
        Notification notification = Notification.reconstitute(
                UUID.randomUUID(), tenantId, userId,
                NotificationType.TASK_CREATED, UUID.randomUUID(), "Read notification",
                true, java.time.Instant.now());

        Notification saved = adapter.save(notification);

        Optional<Notification> found = adapter.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().isRead()).isTrue();
    }

    // ── T-C-04: findByTenantIdAndRecipientUserId() returns correct subset ─

    @Test
    void findByTenantIdAndRecipientUserId_returnsNotificationsForUserOnly() {
        UUID otherUserId = UUID.randomUUID();
        adapter.save(Notification.create(tenantId, userId, NotificationType.TASK_CREATED,
                UUID.randomUUID(), "Notification 1"));
        adapter.save(Notification.create(tenantId, userId, NotificationType.TASK_ASSIGNED,
                UUID.randomUUID(), "Notification 2"));
        adapter.save(Notification.create(tenantId, otherUserId, NotificationType.TASK_CREATED,
                UUID.randomUUID(), "Other user notification"));

        List<Notification> result = adapter.findByTenantIdAndRecipientUserId(tenantId, userId);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(n -> n.getRecipientUserId().equals(userId));
    }

    @Test
    void findByTenantIdAndRecipientUserId_whenNone_returnsEmptyList() {
        List<Notification> result = adapter.findByTenantIdAndRecipientUserId(tenantId, userId);

        assertThat(result).isEmpty();
    }

    // ── T-C-04: countUnread() returns unread count ─────────────────────────

    @Test
    void countUnread_countsOnlyUnreadForUser() {
        adapter.save(Notification.create(tenantId, userId, NotificationType.TASK_ASSIGNED,
                UUID.randomUUID(), "Unread 1"));
        adapter.save(Notification.create(tenantId, userId, NotificationType.TASK_CREATED,
                UUID.randomUUID(), "Unread 2"));
        adapter.save(Notification.reconstitute(UUID.randomUUID(), tenantId, userId,
                NotificationType.TASK_STATUS_CHANGED, UUID.randomUUID(), "Read one",
                true, java.time.Instant.now()));

        int count = adapter.countUnread(tenantId, userId);

        assertThat(count).isEqualTo(2);
    }

    @Test
    void countUnread_whenNoUnread_returnsZero() {
        adapter.save(Notification.reconstitute(UUID.randomUUID(), tenantId, userId,
                NotificationType.TASK_ASSIGNED, UUID.randomUUID(), "Read notification",
                true, java.time.Instant.now()));

        int count = adapter.countUnread(tenantId, userId);

        assertThat(count).isZero();
    }

    // ── T-C-04: markAllAsRead() marks all unread for user ─────────────────

    @Test
    void markAllAsRead_marksAllUnreadNotificationsAsRead() {
        adapter.save(Notification.create(tenantId, userId, NotificationType.TASK_ASSIGNED,
                UUID.randomUUID(), "Unread 1"));
        adapter.save(Notification.create(tenantId, userId, NotificationType.TASK_CREATED,
                UUID.randomUUID(), "Unread 2"));

        adapter.markAllAsRead(tenantId, userId);

        assertThat(adapter.countUnread(tenantId, userId)).isZero();
    }
}
