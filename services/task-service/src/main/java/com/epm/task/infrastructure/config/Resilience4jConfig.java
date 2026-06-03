package com.epm.task.infrastructure.config;

import java.time.Duration;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Resilience4j Circuit Breaker and TimeLimiter configuration for task-service.
 *
 * <p>Instance name: {@code projectService}
 * <ul>
 *   <li>CB: failureRate=50%, minCalls=3, slidingWindow=5, waitDuration=30s</li>
 *   <li>TimeLimiter: timeout=2s</li>
 * </ul>
 */
@Configuration
public class Resilience4jConfig {

    private static final String INSTANCE_NAME = "projectService";

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .minimumNumberOfCalls(3)
                .slidingWindowSize(5)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        registry.circuitBreaker(INSTANCE_NAME, config);
        return registry;
    }

    @Bean
    public TimeLimiterRegistry timeLimiterRegistry() {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(2))
                .build();

        TimeLimiterRegistry registry = TimeLimiterRegistry.of(config);
        registry.timeLimiter(INSTANCE_NAME, config);
        return registry;
    }
}
