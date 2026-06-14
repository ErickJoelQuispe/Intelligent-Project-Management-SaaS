package com.epm.user.infrastructure.adapter.in.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Idempotency claim guard for Kafka consumers.
 *
 * <p>Claims a {@code processed_events} row in its OWN committed transaction (delegated to
 * {@link ProcessedEventClaimer#insertClaim}, which is {@code REQUIRES_NEW}). This is the key
 * to avoiding the {@code UnexpectedRollbackException} trap when two concurrent deliveries of
 * the same eventId race.
 *
 * <p><strong>Why this bean is NOT {@code @Transactional}.</strong> When the loser's INSERT
 * hits the primary-key constraint, Spring marks the surrounding transaction
 * <em>rollback-only</em>. Catching the {@link DataIntegrityViolationException}
 * <em>inside</em> that same transactional boundary does NOT clear the marker — so when that
 * method returns, Spring still tries to commit a rollback-only transaction and throws
 * {@code UnexpectedRollbackException}. The fix is to let the exception PROPAGATE out of the
 * {@code REQUIRES_NEW} boundary ({@link ProcessedEventClaimer}), which rolls that inner
 * transaction back cleanly, and to catch it HERE — outside any transaction — so the caller's
 * own transaction is never poisoned.
 */
@Component
public class IdempotencyGuard {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyGuard.class);

    private final ProcessedEventClaimer claimer;

    public IdempotencyGuard(ProcessedEventClaimer claimer) {
        this.claimer = claimer;
    }

    /**
     * Attempts to claim the given eventId. The actual INSERT runs in a brand-new
     * {@code REQUIRES_NEW} transaction that commits (or rolls back) independently of the
     * caller's; a duplicate's {@link DataIntegrityViolationException} is caught here, outside
     * any transaction, so neither the inner nor the caller's transaction ends up poisoned.
     *
     * @param eventId the envelope eventId to claim
     * @param topic   the source topic (stored for diagnostics)
     * @return {@code true} if this call won the claim (event not seen before);
     *         {@code false} if the eventId was already claimed (benign duplicate)
     */
    public boolean claim(String eventId, String topic) {
        try {
            claimer.insertClaim(eventId, topic);
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            // The REQUIRES_NEW transaction has already rolled back cleanly by the time we get
            // here. Caught outside any transaction, so nothing is left rollback-only.
            log.debug("Event {} already claimed on topic {} — benign duplicate", eventId, topic);
            return false;
        }
    }
}
