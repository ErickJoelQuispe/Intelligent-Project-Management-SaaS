package com.epm.task.domain.port.out;

import java.util.UUID;

import com.epm.task.domain.exception.ProjectServiceUnavailableException;

/**
 * Driven port for verifying project membership via the project-service.
 */
public interface ProjectMembershipPort {

    /**
     * Returns {@code true} if the given user is a member of the specified project.
     *
     * @param projectId the project to check
     * @param userId    the user to verify
     * @param tenantId  tenant scope
     * @return {@code true} if the user is a member
     * @throws ProjectServiceUnavailableException if the circuit breaker is open or request times out
     */
    boolean isMember(UUID projectId, UUID userId, UUID tenantId);
}
