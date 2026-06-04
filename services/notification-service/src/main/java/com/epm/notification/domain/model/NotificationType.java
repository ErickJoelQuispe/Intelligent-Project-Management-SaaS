package com.epm.notification.domain.model;

/**
 * Types of notifications generated from task-domain events.
 */
public enum NotificationType {
    TASK_CREATED,
    TASK_ASSIGNED,
    TASK_STATUS_CHANGED,
    TASK_DELETED
}
