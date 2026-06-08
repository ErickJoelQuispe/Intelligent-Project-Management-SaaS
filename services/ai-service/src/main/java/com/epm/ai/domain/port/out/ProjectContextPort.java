package com.epm.ai.domain.port.out;

import com.epm.ai.domain.model.ProjectContext;

/**
 * Driven port: fetch project context from the project-service.
 */
public interface ProjectContextPort {

    /**
     * Retrieves the context for a given project and tenant.
     *
     * @throws com.epm.ai.domain.exception.ProjectNotFoundException if the project does not exist
     */
    ProjectContext fetchProjectContext(String projectId, String tenantId);
}
