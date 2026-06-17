package com.epm.ai.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

/**
 * Wiring test for {@link OutboxEventJpaRepository}'s H1 relay-claim queries.
 *
 * <p>A true concurrency RED (two threads racing for the same row) is impractical
 * in a pure unit test — it needs a real Postgres with {@code FOR UPDATE SKIP
 * LOCKED} semantics and no integration-test harness exists in this service yet.
 * Instead this guards the contract: the claim methods exist and are native
 * queries that use {@code FOR UPDATE SKIP LOCKED}. If someone reverts to a
 * lock-free derived query, this fails.
 */
class OutboxEventJpaRepositoryWiringTest {

    @Test
    void lockPendingForRelay_usesNativeSkipLockedQuery() throws Exception {
        Method method = OutboxEventJpaRepository.class.getMethod("lockPendingForRelay");
        Query query = method.getAnnotation(Query.class);

        assertThat(query).as("lockPendingForRelay must carry a @Query").isNotNull();
        assertThat(query.nativeQuery()).as("must be a native query").isTrue();
        assertThat(query.value().toUpperCase()).contains("FOR UPDATE SKIP LOCKED");
    }

    @Test
    void lockFailedForRetry_usesNativeSkipLockedQuery() throws Exception {
        Method method = OutboxEventJpaRepository.class.getMethod("lockFailedForRetry", Instant.class);
        Query query = method.getAnnotation(Query.class);

        assertThat(query).as("lockFailedForRetry must carry a @Query").isNotNull();
        assertThat(query.nativeQuery()).as("must be a native query").isTrue();
        assertThat(query.value().toUpperCase()).contains("FOR UPDATE SKIP LOCKED");
        assertThat(query.value().toUpperCase()).contains("PARKED = FALSE");
    }
}
