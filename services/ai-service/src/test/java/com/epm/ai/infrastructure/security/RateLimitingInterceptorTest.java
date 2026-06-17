package com.epm.ai.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for {@link RateLimitingInterceptor}.
 *
 * <p>Verifies JWT-based rate limiting (userId from the authenticated principal,
 * NOT from a client-controllable header), pass-through for unauthenticated
 * requests, and expired-window eviction.
 */
class RateLimitingInterceptorTest {

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
    private static final int MAX_REQUESTS = 3;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String userId) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userId, "n/a", AuthorityUtils.createAuthorityList("ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private MockHttpServletRequest aiRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/ai/chat");
        return request;
    }

    // ── Test 1: authenticated principal is rate-limited after maxRequests ─────

    @Test
    void preHandle_rateLimitsAuthenticatedUser_afterMaxRequests() throws Exception {
        RateLimitingInterceptor interceptor = new RateLimitingInterceptor(MAX_REQUESTS);
        authenticateAs(USER_ID);

        // First MAX_REQUESTS calls pass.
        for (int i = 0; i < MAX_REQUESTS; i++) {
            boolean allowed = interceptor.preHandle(aiRequest(), new MockHttpServletResponse(), new Object());
            assertThat(allowed).as("call %d should be allowed", i + 1).isTrue();
        }

        // The next call exceeds the limit → blocked with 429.
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        boolean blocked = interceptor.preHandle(aiRequest(), blockedResponse, new Object());

        assertThat(blocked).isFalse();
        assertThat(blockedResponse.getStatus()).isEqualTo(429);
    }

    // ── Test 2: no authentication → pass through ──────────────────────────────

    @Test
    void preHandle_passesThrough_whenNoAuthentication() throws Exception {
        RateLimitingInterceptor interceptor = new RateLimitingInterceptor(MAX_REQUESTS);
        SecurityContextHolder.clearContext();

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(aiRequest(), response, new Object());

        assertThat(allowed).isTrue();
        assertThat(response.getStatus()).isEqualTo(200); // unchanged
    }

    // ── Test 3: anonymous principal → pass through (not rate-limited as shared bucket) ──

    @Test
    void preHandle_passesThrough_whenAnonymousAuthentication() throws Exception {
        RateLimitingInterceptor interceptor = new RateLimitingInterceptor(MAX_REQUESTS);
        // Spring Security always populates an AnonymousAuthenticationToken when no JWT is present.
        // isAuthenticated() returns true for anonymous — the guard must explicitly check for this type.
        Authentication anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        SecurityContextHolder.getContext().setAuthentication(anonymous);

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(aiRequest(), response, new Object());

        assertThat(allowed).isTrue();
        assertThat(response.getStatus()).isEqualTo(200); // unchanged, not 429
    }

    // ── Test 4: eviction lets a previously-seen user request again ────────────

    @Test
    void evictExpiredCounters_removesExpiredEntries_andAllowsUserAgain() throws Exception {
        // Window long enough that the rapid calls below land in one window,
        // but short enough that a sleep reliably expires it.
        long windowMs = 200L;
        RateLimitingInterceptor interceptor =
                new RateLimitingInterceptor(MAX_REQUESTS, windowMs);
        authenticateAs(USER_ID);

        // Exhaust the limit within the window — a counter entry now exists.
        for (int i = 0; i < MAX_REQUESTS; i++) {
            interceptor.preHandle(aiRequest(), new MockHttpServletResponse(), new Object());
        }
        assertThat(interceptor.trackedCounters()).isEqualTo(1);
        // Confirm the user is currently blocked.
        assertThat(interceptor.preHandle(aiRequest(), new MockHttpServletResponse(), new Object()))
                .isFalse();

        // Let the window expire, then evict — the stale entry must be removed.
        Thread.sleep(windowMs + 20L);
        interceptor.evictExpiredCounters();
        assertThat(interceptor.trackedCounters())
                .as("expired counter must be evicted to bound map growth")
                .isZero();

        // And a fresh request is allowed again.
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(aiRequest(), response, new Object());
        assertThat(allowed).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
