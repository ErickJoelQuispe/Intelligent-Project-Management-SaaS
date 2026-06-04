package com.epm.notification.domain.model;

/**
 * Types of notifications generated from domain events.
 *
 * <p>Phase 6 adds membership and project notification types.
 * Full set of enum values (WU-C will add PROJECT_CREATED, PROJECT_ARCHIVED, TEAM_ASSIGNED_TO_PROJECT).
 */
public enum NotificationType {
    TASK_CREATED,
    TASK_ASSIGNED,
    TASK_STATUS_CHANGED,
    TASK_DELETED,
    MEMBER_JOINED_TEAM,
    MEMBER_LEFT_TEAM
}
