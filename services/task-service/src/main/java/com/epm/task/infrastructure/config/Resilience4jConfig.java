package com.epm.task.infrastructure.config;

import java.time.Duration;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Resilience4j Circuit Breaker configuration for task-service.
 *
 * <p>Instance name: {@code projectService}
 * <ul>
 *   <li>CB: failureRate=50%, minCalls=3, slidingWindow=5, waitDuration=30s</li>
 * </ul>
 *
 * <p><strong>Timeout enforcement</strong>: the 2-second timeout for project-service
 * calls is enforced at the Feign client layer via
 * {@code spring.cloud.openfeign.client.config.project-service} in {@code application.yml}
 * (connect=2s, read=2s). The key matches {@code @FeignClient(name = "project-service")}.
 * A hung project-service call therefore
 * throws a {@code SocketTimeoutException} → the circuit breaker counts it as a failure
 * and opens after reaching the failure-rate threshold → the fallback throws
 * {@link com.epm.task.domain.exception.ProjectServiceUnavailableException} (fail-closed).
 *
 * <p>The {@code @TimeLimiter} annotation was removed: it requires {@code CompletableFuture}
 * return types and is not applicable to synchronous Feign calls. The Feign read timeout
 * is simpler and more reliable for this use case.
 */
@Configuration
public class Resilience4jConfig {

    private static final String INSTANCE_NAME = "projectService";

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .minimumNumberOfCalls(5)
                .slidingWindowSize(10)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        registry.circuitBreaker(INSTANCE_NAME, config);
        return registry;
    }
}
