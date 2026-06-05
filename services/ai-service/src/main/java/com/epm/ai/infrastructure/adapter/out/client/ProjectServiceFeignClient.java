package com.epm.ai.infrastructure.adapter.out.client;

import com.epm.ai.infrastructure.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Feign client for project-service.
 *
 * <p>Calls {@code GET /api/v1/projects/{id}} to fetch project context for AI operations.
 */
@FeignClient(name = "project-service", configuration = FeignConfig.class)
public interface ProjectServiceFeignClient {

    @GetMapping("/api/v1/projects/{id}")
    ProjectResponse getProject(
            @PathVariable("id") String projectId,
            @RequestHeader("X-Tenant-Id") String tenantId);
}
