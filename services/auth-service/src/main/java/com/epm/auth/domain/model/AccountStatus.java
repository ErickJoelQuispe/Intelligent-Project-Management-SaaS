package com.epm.auth.domain.model;

/**
 * Lifecycle states for an {@link Account}.
 *
 * <p>Pure Java enum — no Spring, no JPA annotations.
 */
public enum AccountStatus {
    ACTIVE,
    LOCKED,
    SUSPENDED
}
