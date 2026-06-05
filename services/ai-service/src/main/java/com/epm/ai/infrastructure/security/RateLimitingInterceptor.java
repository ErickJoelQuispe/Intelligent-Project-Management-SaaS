package com.epm.ai.infrastructure.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Simple sliding-window rate limiter per user.
 *
 * <p>Tracks request counts per user within a configurable time window.
 * When the limit is exceeded, returns HTTP 429 Too Many Requests.
 */
@Component
public class RateLimitingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingInterceptor.class);
    private static final long WINDOW_MS = 60_000L; // 1 minute

    private final int maxRequests;

    /** userId → (windowStartEpoch, count) */
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public RateLimitingInterceptor(@Value("${ai.rate-limit.default:20}") int maxRequests) {
        this.maxRequests = maxRequests;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        String path = request.getRequestURI();
        // Only rate-limit AI endpoints
        if (!path.startsWith("/api/v1/ai/")) {
            return true;
        }

        // Extract user from header (set by JwtTokenFilter)
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) {
            return true; // No user context — let security handle it
        }

        if (isRateLimited(userId)) {
            log.warn("Rate limit exceeded for user {}", userId);
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Too many requests. Please wait before trying again.\"," +
                    "\"code\":\"RATE_LIMIT_EXCEEDED\"}");
            return false;
        }

        return true;
    }

    private boolean isRateLimited(String userId) {
        long now = System.currentTimeMillis();
        WindowCounter counter = counters.compute(userId, (key, existing) -> {
            if (existing == null || (now - existing.windowStart) > WINDOW_MS) {
                return new WindowCounter(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });
        return counter.count.get() > maxRequests;
    }

    private record WindowCounter(long windowStart, AtomicInteger count) {}
}
