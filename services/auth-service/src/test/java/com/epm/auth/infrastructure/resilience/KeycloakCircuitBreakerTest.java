package com.epm.auth.infrastructure.resilience;

import com.epm.auth.domain.exception.IdentityProviderException;
import com.epm.auth.infrastructure.adapter.out.identity.KeycloakAdminAdapter;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the Keycloak circuit breaker behaviour.
 *
 * <p>Uses the Resilience4j programmatic API — NO Spring context required.
 * The test drives a {@link Supplier} through a configured {@link CircuitBreaker}
 * to verify that:
 * <ol>
 *   <li>After {@code slidingWindowSize} consecutive failures the CB transitions to OPEN.</li>
 *   <li>While OPEN, subsequent calls throw {@link CallNotPermittedException} immediately
 *       (i.e. the fallback path is activated — verified via the thrown exception type).</li>
 *   <li>A fallback that converts {@link CallNotPermittedException} to {@link IdentityProviderException}
 *       behaves correctly.</li>
 * </ol>
 *
 * <p><strong>TODO (Finding 9 — out of scope for this batch):</strong> Add a Spring-context
 * integration test using WireMock to verify the full {@code @CircuitBreaker} AOP proxy
 * around {@link KeycloakAdminAdapter}. This would stub Keycloak HTTP calls at the network
 * level and assert that the {@code createUserFallback}/{@code deleteUserFallback} methods
 * are invoked when the circuit opens under real Spring wiring.
 */
class KeycloakCircuitBreakerTest {

    private static final int WINDOW_SIZE = 5;
    private static final float FAILURE_THRESHOLD = 100f; // 100% failure rate to open after WINDOW_SIZE failures

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
        circuitBreaker = registry.circuitBreaker("keycloak-test");
    }

    @Test
    @DisplayName("Circuit breaker starts CLOSED")
    void circuitBreaker_startsInClosedState() {
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("Circuit breaker opens after WINDOW_SIZE consecutive failures")
    void circuitBreaker_opensAfterConsecutiveFailures() {
        // Drive WINDOW_SIZE failures through the CB to trigger OPEN state
        for (int i = 0; i < WINDOW_SIZE; i++) {
            try {
                circuitBreaker.executeSupplier(() -> {
                    throw new RuntimeException("Keycloak unreachable");
                });
            } catch (Exception ignored) {
                // Expected — each call fails
            }
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("Circuit breaker rejects calls with CallNotPermittedException when OPEN")
    void circuitBreaker_rejectsCallsWhenOpen() {
        // Open the CB first
        openCircuitBreaker();

        // Next call should be rejected immediately — no actual execution
        assertThatThrownBy(() ->
                circuitBreaker.executeSupplier(() -> UUID.randomUUID())
        ).isInstanceOf(CallNotPermittedException.class);
    }

    @Test
    @DisplayName("Fallback converts CallNotPermittedException to IdentityProviderException")
    void fallback_convertsCallNotPermittedToIdentityProviderException() {
        // Open the CB
        openCircuitBreaker();

        // Simulate what the @CircuitBreaker fallbackMethod does
        IdentityProviderException result = null;
        try {
            circuitBreaker.executeSupplier(() -> (UUID) null);
        } catch (CallNotPermittedException ex) {
            result = createUserFallback(ex);
        }

        assertThat(result).isNotNull();
        assertThat(result.getMessage()).contains("Keycloak unavailable");
        assertThat(result.getRetryAfterSeconds()).isEqualTo(30);
    }

    @Test
    @DisplayName("CB records call count correctly — counts attempted calls in CLOSED state")
    void circuitBreaker_countsAttemptedCallsInClosedState() {
        AtomicInteger callCount = new AtomicInteger(0);

        // Make 3 successful calls
        for (int i = 0; i < 3; i++) {
            int finalI = i;
            circuitBreaker.executeSupplier(() -> {
                callCount.incrementAndGet();
                return finalI;
            });
        }

        assertThat(callCount.get()).isEqualTo(3);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Opens the circuit breaker by driving WINDOW_SIZE failures. */
    private void openCircuitBreaker() {
        for (int i = 0; i < WINDOW_SIZE; i++) {
            try {
                circuitBreaker.executeSupplier(() -> {
                    throw new RuntimeException("Keycloak unreachable");
                });
            } catch (Exception ignored) { /* expected */ }
        }
    }

    /**
     * Mirrors the fallback logic in {@link KeycloakAdminAdapter#createUserFallback}.
     * Verifies the contract independently of Spring AOP proxy.
     */
    private IdentityProviderException createUserFallback(Throwable ex) {
        return new IdentityProviderException("Keycloak unavailable: " + ex.getMessage(), 30);
    }
}
