package com.epm.task.infrastructure.adapter.out.client;

import java.util.UUID;

import com.epm.task.domain.exception.ProjectServiceUnavailableException;
import com.epm.task.domain.port.out.ProjectMembershipPort;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Implements {@link ProjectMembershipPort} via Feign + Resilience4j Circuit Breaker.
 *
 * <p>Returns {@code true} on HTTP 200, {@code false} on HTTP 404 (not a member),
 * and throws {@link ProjectServiceUnavailableException} if the circuit is open or times out.
 *
 * <p><strong>Cache</strong>: membership results are cached for 30 s (Caffeine, max 10 000
 * entries) keyed by {@code (projectId, userId, tenantId)}. A just-added member may wait
 * up to 30 s before their new membership is visible to task mutations — this is acceptable.
 * Only successful {@code true}/{@code false} returns are cached; exceptions (e.g. circuit
 * breaker open → {@link ProjectServiceUnavailableException}) are NOT cached, so a
 * recovered project-service is tried immediately on the next call.
 *
 * <p><strong>AOP ordering</strong>: aspect order is determined by each interceptor's
 * {@link org.springframework.core.Ordered} value (lower value = higher precedence = OUTER).
 * With Spring defaults the Resilience4j {@code CircuitBreakerAspect} runs at
 * {@code Ordered.LOWEST_PRECEDENCE - 4} (= {@code Integer.MAX_VALUE - 4}; see
 * resilience4j-spring6 {@code CircuitBreakerConfigurationProperties#circuitBreakerAspectOrder}),
 * while the Spring cache interceptor uses the {@code @EnableCaching} default of
 * {@code Ordered.LOWEST_PRECEDENCE} (= {@code Integer.MAX_VALUE}; not overridden in
 * {@code CacheConfig}). Because the circuit breaker's order value is lower, it has higher
 * precedence and is the OUTER advice, wrapping the cache interceptor (INNER). So the circuit
 * breaker wraps the cache lookup, not the other way around. This is acceptable: a cache hit
 * is an in-memory operation that never throws, so it is never recorded as a circuit-breaker
 * failure and cannot open the circuit. The breaker only ever counts real Feign calls (cache
 * misses). The one consequence is that while the circuit is OPEN, even a cached
 * {@code true}/{@code false} is not served — the fallback throws
 * {@link ProjectServiceUnavailableException} first.
 */
@Component
public class ProjectMembershipFeignAdapter implements ProjectMembershipPort {

    private static final Logger log = LoggerFactory.getLogger(ProjectMembershipFeignAdapter.class);

    private final ProjectServiceFeignClient feignClient;

    public ProjectMembershipFeignAdapter(ProjectServiceFeignClient feignClient) {
        this.feignClient = feignClient;
    }

    /**
     * Checks whether the given user is a member of the project.
     *
     * <p>Result is cached for 30 s (see {@code CacheConfig}). Exceptions thrown by the
     * circuit-breaker fallback are never cached — only boolean return values are.
     *
     * @param projectId the project to check
     * @param userId    the user to verify
     * @param tenantId  tenant scope (included in cache key for correctness)
     * @return {@code true} if the user is a member, {@code false} otherwise
     * @throws ProjectServiceUnavailableException if the circuit is open or the request times out
     */
    @Override
    @Cacheable(value = "membershipCache", key = "#projectId + ':' + #userId + ':' + #tenantId")
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
