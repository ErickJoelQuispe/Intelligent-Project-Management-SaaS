package com.epm.project.infrastructure.adapter.in.rest;

import java.util.UUID;

/**
 * Response body for project membership check.
 *
 * <p>Consumed by the Feign client in task-service to determine if a user
 * is a member of a given project.
 */
public record MembershipResponse(UUID projectId, UUID userId, boolean member) {
}
