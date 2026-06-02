package com.epm.project.domain.port.in;

import java.util.UUID;

/**
 * Driving port: archives a project.
 */
public interface ArchiveProjectUseCase {

    void execute(UUID projectId, UUID callerProfileId, UUID tenantId);
}
