package com.epm.task.infrastructure.config;

import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Cache configuration backed by Caffeine for task-service.
 *
 * <p>Registers a single cache: {@code membershipCache} — short-lived (30 s TTL)
 * boolean results from {@code ProjectMembershipFeignAdapter.isMember}. This prevents
 * hammering the project-service on every task mutation while keeping the staleness
 * window short enough that a newly added member can mutate tasks within 30 s.
 *
 * <p><strong>Staleness window</strong>: a user removed from a project may still mutate
 * its tasks for up to 30 s after removal. A user added to a project may wait up to 30 s
 * before mutations are allowed. This is acceptable for a CRUD task service.
 *
 * <p>{@code maximumSize(10_000)} caps memory at roughly 10 000 (project, user, tenant)
 * triples — well within JVM heap for typical tenant sizes.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("membershipCache");
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .maximumSize(10_000));
        return manager;
    }
}
