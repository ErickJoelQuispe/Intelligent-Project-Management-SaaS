package com.epm.ai.infrastructure.adapter.out.messaging;

import java.time.Instant;
import java.util.List;

import com.epm.ai.infrastructure.adapter.out.persistence.OutboxEventJpaEntity;
import com.epm.ai.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional executor for outbox relay operations.
 *
 * <p>Extracted from {@link OutboxRelayService} into a separate {@code @Component} so that
 * Spring AOP can proxy {@code @Transactional(REQUIRES_NEW)} methods correctly. When
 * {@code relayPending()} and {@code retryFailed()} lived on the same bean as the
 * {@code @Scheduled} / {@code @TransactionalEventListener} callers, self-invocation
 * ({@code this.relayPending()}) bypassed the proxy and the {@code REQUIRES_NEW} transaction
 * — together with the {@code FOR UPDATE SKIP LOCKED} row locks — never took effect.
 */
@Component
public class OutboxRelayExecutor {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayExecutor.class);
    private static final long RETRY_THRESHOLD_MS = 30_000L;

    /** Maximum publish attempts before an event is parked as a poison record. */
    static final int MAX_ATTEMPTS = 5;

    private final OutboxEventJpaRepository outboxRepo;
    private final KafkaOutboxPublisher publisher;

    public OutboxRelayExecutor(OutboxEventJpaRepository outboxRepo, KafkaOutboxPublisher publisher) {
        this.outboxRepo = outboxRepo;
        this.publisher = publisher;
    }

    /**
     * Claims and publishes pending events in an isolated {@code REQUIRES_NEW} transaction.
     * The {@code FOR UPDATE SKIP LOCKED} row locks are held until commit, so a concurrent
     * relay (immediate path + scheduler) skips the same rows instead of double-publishing.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void relayPending() {
        List<OutboxEventJpaEntity> pending = outboxRepo.lockPendingForRelay();
        for (OutboxEventJpaEntity entity : pending) {
            log.debug("Relaying outbox event {} to topic {}", entity.getId(), entity.getTopic());
            publisher.publish(entity);
        }
    }

    /**
     * Re-publishes failed events past the retry threshold, parking any that have
     * exhausted {@link #MAX_ATTEMPTS} to prevent poison-event infinite loops.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryFailed() {
        Instant threshold = Instant.now().minusMillis(RETRY_THRESHOLD_MS);
        List<OutboxEventJpaEntity> failed = outboxRepo.lockFailedForRetry(threshold);
        for (OutboxEventJpaEntity entity : failed) {
            if (entity.getAttempts() >= MAX_ATTEMPTS) {
                log.error("Parking poison outbox event {} after {} attempts: {}",
                        entity.getId(), entity.getAttempts(), entity.getError());
                entity.setParked(true);
                outboxRepo.save(entity);
                continue;
            }
            log.warn("Retrying failed outbox event {} to topic {} (attempt {})",
                    entity.getId(), entity.getTopic(), entity.getAttempts());
            // Clear stale state before re-publishing; publish() will set the final state and save.
            entity.setFailedAt(null);
            entity.setError(null);
            publisher.publish(entity);
        }
    }
}
