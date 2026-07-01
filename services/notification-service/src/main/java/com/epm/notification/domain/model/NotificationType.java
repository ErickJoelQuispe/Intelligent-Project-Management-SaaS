package com.epm.notification.domain.model;

/**
 * Types of notifications generated from domain events.
 *
 * <p>Phase 6 WU-B added: MEMBER_JOINED_TEAM, MEMBER_LEFT_TEAM.
 * Phase 6 WU-C adds: PROJECT_CREATED, PROJECT_ARCHIVED, TEAM_ASSIGNED_TO_PROJECT.
 * User registration adds: INVITATION_SENT.
 */
public enum NotificationType {
    TASK_CREATED,
    TASK_ASSIGNED,
    TASK_STATUS_CHANGED,
    TASK_DELETED,
    MEMBER_JOINED_TEAM,
    MEMBER_LEFT_TEAM,
    PROJECT_CREATED,
    PROJECT_ARCHIVED,
    TEAM_ASSIGNED_TO_PROJECT,
    INVITATION_SENT
}
