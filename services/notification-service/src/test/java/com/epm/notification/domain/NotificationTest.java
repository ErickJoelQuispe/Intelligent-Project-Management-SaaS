package com.epm.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.epm.notification.domain.model.Notification;
import com.epm.notification.domain.model.NotificationType;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Notification aggregate root (T-C-02).
 *
 * <p>Pure Java — no Spring context.
 */
class NotificationTest {

    // ── T-C-02: create() sets correct fields ──────────────────────────────

    @Test
    void create_setsAllFieldsCorrectly() {
        UUID tenantId = UUID.randomUUID();
        UUID recipientUserId = UUID.randomUUID();
        UUID referenceId = UUID.randomUUID();

        Notification notification = Notification.create(
                tenantId, recipientUserId, NotificationType.TASK_ASSIGNED,
                referenceId, "Task 'Fix login' was assigned to you");

        assertThat(notification.getId()).isNotNull();
        assertThat(notification.getTenantId()).isEqualTo(tenantId);
        assertThat(notification.getRecipientUserId()).isEqualTo(recipientUserId);
        assertThat(notification.getType()).isEqualTo(NotificationType.TASK_ASSIGNED);
        assertThat(notification.getReferenceId()).isEqualTo(referenceId);
        assertThat(notification.getMessage()).isEqualTo("Task 'Fix login' was assigned to you");
        assertThat(notification.isRead()).isFalse();
        assertThat(notification.getCreatedAt()).isNotNull();
    }

    @Test
    void create_withDifferentType_setsCorrectType() {
        UUID tenantId = UUID.randomUUID();
        UUID recipientUserId = UUID.randomUUID();
        UUID referenceId = UUID.randomUUID();

        Notification notification = Notification.create(
                tenantId, recipientUserId, NotificationType.TASK_STATUS_CHANGED,
                referenceId, "Task status changed to IN_PROGRESS");

        assertThat(notification.getType()).isEqualTo(NotificationType.TASK_STATUS_CHANGED);
        assertThat(notification.getMessage()).isEqualTo("Task status changed to IN_PROGRESS");
        assertThat(notification.isRead()).isFalse();
    }

    // ── T-C-02: markAsRead() changes read flag ────────────────────────────

    @Test
    void markAsRead_changesReadFlagToTrue() {
        Notification notification = Notification.create(
                UUID.randomUUID(), UUID.randomUUID(), NotificationType.TASK_CREATED,
                UUID.randomUUID(), "A new task was created");

        assertThat(notification.isRead()).isFalse();
        notification.markAsRead();
        assertThat(notification.isRead()).isTrue();
    }

    @Test
    void markAsRead_calledTwice_remainsRead() {
        Notification notification = Notification.create(
                UUID.randomUUID(), UUID.randomUUID(), NotificationType.TASK_DELETED,
                UUID.randomUUID(), "Task was deleted");

        notification.markAsRead();
        notification.markAsRead();
        assertThat(notification.isRead()).isTrue();
    }

    // ── T-C-02: reconstitute() restores from persistence ─────────────────

    @Test
    void reconstitute_restoresAllFields() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID recipientUserId = UUID.randomUUID();
        UUID referenceId = UUID.randomUUID();
        java.time.Instant createdAt = java.time.Instant.parse("2026-06-04T10:00:00Z");

        Notification notification = Notification.reconstitute(
                id, tenantId, recipientUserId, NotificationType.TASK_ASSIGNED,
                referenceId, "You have been assigned a task", true, createdAt);

        assertThat(notification.getId()).isEqualTo(id);
        assertThat(notification.getTenantId()).isEqualTo(tenantId);
        assertThat(notification.getRecipientUserId()).isEqualTo(recipientUserId);
        assertThat(notification.getType()).isEqualTo(NotificationType.TASK_ASSIGNED);
        assertThat(notification.getReferenceId()).isEqualTo(referenceId);
        assertThat(notification.getMessage()).isEqualTo("You have been assigned a task");
        assertThat(notification.isRead()).isTrue();
        assertThat(notification.getCreatedAt()).isEqualTo(createdAt);
    }

    // ── T-C-02: NotificationType enum values ─────────────────────────────

    @Test
    void notificationType_hasAllRequiredValues() {
        // Phase 6 WU-B added MEMBER_JOINED_TEAM, MEMBER_LEFT_TEAM.
        // Phase 6 WU-C added PROJECT_CREATED, PROJECT_ARCHIVED, TEAM_ASSIGNED_TO_PROJECT.
        assertThat(NotificationType.values()).containsExactlyInAnyOrder(
                NotificationType.TASK_CREATED,
                NotificationType.TASK_ASSIGNED,
                NotificationType.TASK_STATUS_CHANGED,
                NotificationType.TASK_DELETED,
                NotificationType.MEMBER_JOINED_TEAM,
                NotificationType.MEMBER_LEFT_TEAM,
                NotificationType.PROJECT_CREATED,
                NotificationType.PROJECT_ARCHIVED,
                NotificationType.TEAM_ASSIGNED_TO_PROJECT);
    }
}
