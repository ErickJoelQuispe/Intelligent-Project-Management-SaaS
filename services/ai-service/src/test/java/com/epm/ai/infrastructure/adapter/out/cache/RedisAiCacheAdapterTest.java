package com.epm.ai.infrastructure.adapter.out.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import com.epm.ai.domain.model.AiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Unit tests for {@link RedisAiCacheAdapter}.
 * Uses mocked StringRedisTemplate — no real Redis needed.
 */
@ExtendWith(MockitoExtension.class)
class RedisAiCacheAdapterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private RedisAiCacheAdapter adapter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        adapter = new RedisAiCacheAdapter(redisTemplate, objectMapper);
    }

    // ── cache miss: null from Redis → empty Optional ─────────────────────────

    @Test
    void get_returnsEmpty_onCacheMiss() {
        when(valueOps.get("missing-key")).thenReturn(null);

        Optional<AiResponse> result = adapter.get("missing-key");

        assertThat(result).isEmpty();
    }

    // ── cache hit: valid JSON → AiResponse ───────────────────────────────────

    @Test
    void get_returnsAiResponse_onCacheHit() throws Exception {
        AiResponse expected = new AiResponse("Generated tasks: ...", 100, 50, "deepseek-chat", true);
        String json = objectMapper.writeValueAsString(expected);
        when(valueOps.get("cache-key-1")).thenReturn(json);

        Optional<AiResponse> result = adapter.get("cache-key-1");

        assertThat(result).isPresent();
        assertThat(result.get().content()).isEqualTo("Generated tasks: ...");
        assertThat(result.get().inputTokens()).isEqualTo(100);
        assertThat(result.get().outputTokens()).isEqualTo(50);
        assertThat(result.get().model()).isEqualTo("deepseek-chat");
    }

    // ── cache put: verifies JSON serialization + TTL ──────────────────────────

    @Test
    void put_serializesAiResponseAsJson_withTtl() throws Exception {
        AiResponse response = new AiResponse("Summary text", 80, 40, "deepseek-chat", false);

        adapter.put("summary-key", response, 3600L);

        String expectedJson = objectMapper.writeValueAsString(response);
        verify(valueOps).set(eq("summary-key"), eq(expectedJson), eq(Duration.ofSeconds(3600)));
    }

    // ── cache hit with different content ─────────────────────────────────────

    @Test
    void get_returnsCorrectResponse_forDifferentCachedContent() throws Exception {
        AiResponse response1 = new AiResponse("First response", 10, 5, "deepseek-chat", true);
        AiResponse response2 = new AiResponse("Second response", 20, 10, "deepseek-chat", true);

        String json1 = objectMapper.writeValueAsString(response1);
        String json2 = objectMapper.writeValueAsString(response2);

        when(valueOps.get("key-1")).thenReturn(json1);
        when(valueOps.get("key-2")).thenReturn(json2);

        Optional<AiResponse> result1 = adapter.get("key-1");
        Optional<AiResponse> result2 = adapter.get("key-2");

        assertThat(result1.get().content()).isEqualTo("First response");
        assertThat(result2.get().content()).isEqualTo("Second response");
    }

    // ── malformed JSON → empty Optional (error handling) ─────────────────────

    @Test
    void get_returnsEmpty_onMalformedJson() {
        when(valueOps.get("bad-key")).thenReturn("NOT_VALID_JSON{{{");

        Optional<AiResponse> result = adapter.get("bad-key");

        assertThat(result).isEmpty();
    }
}
