package com.epm.ai.infrastructure.adapter.out.cache;

import java.time.Duration;
import java.util.Optional;

import com.epm.ai.domain.model.AiResponse;
import com.epm.ai.domain.port.out.AiCachePort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed implementation of {@link AiCachePort}.
 *
 * <p>Serializes {@link AiResponse} as JSON (including content, inputTokens, outputTokens,
 * model, cached fields). Deserializes on cache hit.
 */
@Component
public class RedisAiCacheAdapter implements AiCachePort {

    private static final Logger log = LoggerFactory.getLogger(RedisAiCacheAdapter.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisAiCacheAdapter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<AiResponse> get(String key) {
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, AiResponse.class));
        } catch (JsonProcessingException ex) {
            log.warn("Failed to deserialize cached AiResponse for key '{}': {}", key, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String key, AiResponse response, long ttlSeconds) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(ttlSeconds));
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize AiResponse for key '{}': {}", key, ex.getMessage());
        }
    }
}
