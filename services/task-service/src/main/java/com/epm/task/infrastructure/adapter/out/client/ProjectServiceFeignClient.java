package com.epm.task.infrastructure.adapter.out.client;

import java.util.UUID;

import com.epm.task.infrastructure.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client for project-service membership check.
 *
 * <p>Calls {@code GET /api/v1/projects/{projectId}/members/{userId}}.
 * Returns 200 if the user is a member, 404 if not.
 */
@FeignClient(name = "project-service", configuration = FeignConfig.class)
public interface ProjectServiceFeignClient {

    @GetMapping("/api/v1/projects/{projectId}/members/{userId}")
    ResponseEntity<ProjectMemberResponse> checkMembership(
            @PathVariable UUID projectId,
            @PathVariable UUID userId);
}
