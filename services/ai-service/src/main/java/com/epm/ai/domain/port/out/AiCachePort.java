package com.epm.ai.domain.port.out;

import java.util.Optional;

import com.epm.ai.domain.model.AiResponse;

/**
 * Driven port: AI response cache (e.g., Redis).
 */
public interface AiCachePort {

    /**
     * Retrieves a cached AI response by cache key.
     *
     * @return an Optional containing the cached response, or empty on cache miss
     */
    Optional<AiResponse> get(String key);

    /**
     * Stores an AI response in the cache with a time-to-live.
     *
     * @param key        the cache key (SHA-256 of normalized prompt)
     * @param response   the AI response to cache
     * @param ttlSeconds time-to-live in seconds
     */
    void put(String key, AiResponse response, long ttlSeconds);
}
