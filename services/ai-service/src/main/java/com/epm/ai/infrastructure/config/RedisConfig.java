package com.epm.ai.infrastructure.config;

import org.springframework.context.annotation.Configuration;

/**
 * Redis configuration for ai-service.
 *
 * <p>{@code StringRedisTemplate} is auto-configured by Spring Boot's
 * {@code spring-boot-starter-data-redis} starter from {@code spring.data.redis.*} properties.
 * No explicit bean declaration is needed.
 */
@Configuration
public class RedisConfig {
    // Spring Boot auto-configures LettuceConnectionFactory and StringRedisTemplate
    // from spring.data.redis.host / spring.data.redis.port properties.
}
