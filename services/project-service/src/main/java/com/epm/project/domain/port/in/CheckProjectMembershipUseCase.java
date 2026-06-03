package com.epm.project.domain.port.in;

import java.util.UUID;

/**
 * Driving port: checks whether a given user is an active member of a project.
 *
 * <p>Used by the Feign client in task-service to validate project membership
 * before allowing task creation.
 */
public interface CheckProjectMembershipUseCase {

    /**
     * Returns {@code true} if the specified user is an active member of the project.
     *
     * @param projectId the project to check
     * @param userId    the user whose membership is being verified
     * @param tenantId  the tenant scope (ensures cross-tenant isolation)
     * @return {@code true} if the user is an active member, {@code false} otherwise
     */
    boolean isMember(UUID projectId, UUID userId, UUID tenantId);
}
