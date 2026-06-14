package com.epm.project.domain.model;

/**
 * Lifecycle status of a project.
 *
 * <p>COMPLETED was removed — it was dead code that was never reachable
 * through any use case. The DB migration V007 drops it from the CHECK constraint.
 */
public enum ProjectStatus {
    ACTIVE,
    ARCHIVED
}
