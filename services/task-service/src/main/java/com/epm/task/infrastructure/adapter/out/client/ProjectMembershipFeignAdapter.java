package com.epm.task.infrastructure.adapter.out.client;

import java.util.UUID;

import com.epm.task.domain.exception.ProjectServiceUnavailableException;
import com.epm.task.domain.port.out.ProjectMembershipPort;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Implements {@link ProjectMembershipPort} via Feign + Resilience4j Circuit Breaker.
 *
 * <p>Returns {@code true} on HTTP 200, {@code false} on HTTP 404 (not a member),
 * and throws {@link ProjectServiceUnavailableException} if the circuit is open or times out.
 */
@Component
public class ProjectMembershipFeignAdapter implements ProjectMembershipPort {

    private static final Logger log = LoggerFactory.getLogger(ProjectMembershipFeignAdapter.class);

    private final ProjectServiceFeignClient feignClient;

    public ProjectMembershipFeignAdapter(ProjectServiceFeignClient feignClient) {
        this.feignClient = feignClient;
    }

    @Override
    @CircuitBreaker(name = "projectService", fallbackMethod = "isMemberFallback")
    public boolean isMember(UUID projectId, UUID userId, UUID tenantId) {
        try {
            ResponseEntity<ProjectMemberResponse> response =
                    feignClient.checkMembership(projectId, userId);
            return response.getStatusCode().is2xxSuccessful();
        } catch (FeignException.NotFound e) {
            log.debug("User {} is not a member of project {}", userId, projectId);
            return false;
        }
    }

    @SuppressWarnings("unused")
    public boolean isMemberFallback(UUID projectId, UUID userId, UUID tenantId, Throwable cause) {
        log.warn("Circuit breaker open for project-service: {}", cause.getMessage());
        throw new ProjectServiceUnavailableException(
                "project-service is unavailable: " + cause.getMessage());
    }
}
