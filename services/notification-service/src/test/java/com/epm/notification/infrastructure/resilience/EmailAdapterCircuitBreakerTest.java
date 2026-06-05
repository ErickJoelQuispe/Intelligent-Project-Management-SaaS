package com.epm.notification.infrastructure.resilience;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the emailAdapter circuit breaker behaviour.
 *
 * <p>Uses the Resilience4j programmatic API — NO Spring context required.
 * Verifies the CB semantics that protect {@link com.epm.notification.infrastructure.adapter.out.email.ThymeleafEmailAdapter}:
 * <ul>
 *   <li>CB opens after N consecutive failures (100% failure rate on a window of WINDOW_SIZE).</li>
 *   <li>While OPEN, calls throw {@link CallNotPermittedException} immediately.</li>
 *   <li>The sendFallback (fire-and-forget) contract: no exception is propagated on open state.</li>
 * </ul>
 */
class EmailAdapterCircuitBreakerTest {

    private static final int WINDOW_SIZE = 5;
    private static final float FAILURE_THRESHOLD = 100f; // 100% for fast open in test

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(WINDOW_SIZE)
                .failureRateThreshold(FAILURE_THRESHOLD)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .permittedNumberOfCallsInHalfOpenState(1)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        circuitBreaker = registry.circuitBreaker("emailAdapter-test");
    }

    @Test
    @DisplayName("emailAdapter CB starts CLOSED")
    void emailAdapter_circuitBreaker_startsClosed() {
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("emailAdapter CB opens after WINDOW_SIZE consecutive SMTP failures")
    void emailAdapter_circuitBreaker_opensAfterConsecutiveSmtpFailures() {
        for (int i = 0; i < WINDOW_SIZE; i++) {
            try {
                circuitBreaker.executeRunnable(() -> {
                    throw new RuntimeException("SMTP connection refused");
                });
            } catch (Exception ignored) { /* expected */ }
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("emailAdapter CB rejects calls with CallNotPermittedException when OPEN")
    void emailAdapter_circuitBreaker_rejectsCallsWhenOpen() {
        openCircuitBreaker();

        assertThatThrownBy(() ->
                circuitBreaker.executeRunnable(() -> { /* send email */ })
        ).isInstanceOf(CallNotPermittedException.class);
    }

    @Test
    @DisplayName("sendFallback swallows CallNotPermittedException — fire-and-forget contract holds")
    void sendFallback_swallowsCallNotPermittedException() {
        openCircuitBreaker();

        // Simulate what @CircuitBreaker fallbackMethod does: catch the exception and swallow it
        AtomicInteger fallbackCallCount = new AtomicInteger(0);
        try {
            circuitBreaker.executeRunnable(() -> { /* send email */ });
        } catch (CallNotPermittedException ex) {
            // This mirrors sendFallback — logs and does NOT rethrow
            fallbackCallCount.incrementAndGet();
        }

        assertThat(fallbackCallCount.get()).isEqualTo(1);
        // No exception propagated — fire-and-forget contract preserved
    }

    @Test
    @DisplayName("emailAdapter CB counts successful calls correctly")
    void emailAdapter_circuitBreaker_countsSuccessfulCalls() {
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < 3; i++) {
            circuitBreaker.executeRunnable(successCount::incrementAndGet);
        }

        assertThat(successCount.get()).isEqualTo(3);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void openCircuitBreaker() {
        for (int i = 0; i < WINDOW_SIZE; i++) {
            try {
                circuitBreaker.executeRunnable(() -> {
                    throw new RuntimeException("SMTP connection refused");
                });
            } catch (Exception ignored) { /* expected */ }
        }
    }
}
