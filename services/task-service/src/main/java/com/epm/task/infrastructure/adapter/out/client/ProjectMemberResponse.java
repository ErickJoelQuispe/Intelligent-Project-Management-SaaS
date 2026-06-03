package com.epm.task.infrastructure.adapter.out.client;

/**
 * Response DTO from project-service membership check endpoint.
 */
public record ProjectMemberResponse(boolean isMember) {
}
