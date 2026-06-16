package com.epm.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import com.epm.notification.infrastructure.adapter.out.persistence.ProcessedEventJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;

/**
 * FIX B regression guard: the native {@code claimEvent} INSERT shares ONE {@code @Transactional}
 * session with the consumer's business dispatch. If {@code @Modifying} does not set
 * {@code flushAutomatically} and {@code clearAutomatically}, Hibernate's L1 cache can:
 * <ul>
 *   <li>serve a STALE entity on a subsequent JPA read in the same transaction
 *       (missing {@code clearAutomatically}), and/or</li>
 *   <li>reorder the native INSERT relative to pending dirty entities
 *       (missing {@code flushAutomatically}).</li>
 * </ul>
 *
 * <p>This is a pure-reflection guard with NO Spring context or DB — deterministic and fast. It is
 * genuinely RED if either attribute is dropped from the annotation: the assertions on
 * {@code flushAutomatically()} / {@code clearAutomatically()} fail. A behavioral L1-cache test is
 * non-deterministic to force, so this metadata guard is the honest lock on the contract.
 */
class ClaimEventModifyingAttributesTest {

    @Test
    void claimEvent_modifyingAnnotation_hasFlushAndClearAutomatically() throws NoSuchMethodException {
        Method claimEvent = ProcessedEventJpaRepository.class.getMethod(
                "claimEvent", String.class, String.class, java.time.Instant.class);

        Modifying modifying = claimEvent.getAnnotation(Modifying.class);

        assertThat(modifying)
                .as("claimEvent must be annotated @Modifying (it is a native INSERT)")
                .isNotNull();

        assertThat(modifying.flushAutomatically())
                .as("@Modifying.flushAutomatically must be true so pending dirty entities are "
                        + "flushed to the DB BEFORE the native INSERT runs in the shared transaction")
                .isTrue();

        assertThat(modifying.clearAutomatically())
                .as("@Modifying.clearAutomatically must be true so the L1 cache is evicted and a "
                        + "subsequent JPA read in the SAME transaction sees DB truth, not a stale snapshot")
                .isTrue();
    }
}
