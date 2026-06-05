package com.epm.ai.infrastructure.adapter.out.client;

import java.util.List;

import com.epm.ai.domain.exception.ProjectNotFoundException;
import com.epm.ai.domain.model.ProjectContext;
import com.epm.ai.domain.port.out.ProjectContextPort;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implements {@link ProjectContextPort} by delegating to {@link ProjectServiceFeignClient}.
 *
 * <p>Maps Feign 404 exceptions to {@link ProjectNotFoundException}.
 */
@Component
public class ProjectContextFeignAdapter implements ProjectContextPort {

    private static final Logger log = LoggerFactory.getLogger(ProjectContextFeignAdapter.class);

    private final ProjectServiceFeignClient feignClient;

    public ProjectContextFeignAdapter(ProjectServiceFeignClient feignClient) {
        this.feignClient = feignClient;
    }

    @Override
    public ProjectContext fetchProjectContext(String projectId, String tenantId) {
        try {
            ProjectResponse response = feignClient.getProject(projectId, tenantId);
            List<String> memberNames = response.memberNames() != null ? response.memberNames() : List.of();
            return new ProjectContext(
                    response.id(),
                    response.name(),
                    response.description() != null ? response.description() : "",
                    memberNames);
        } catch (FeignException.NotFound ex) {
            log.warn("Project not found in project-service: projectId={}", projectId);
            throw new ProjectNotFoundException(projectId);
        }
    }
}
