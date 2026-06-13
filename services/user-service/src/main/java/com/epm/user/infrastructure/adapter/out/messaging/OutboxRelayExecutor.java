package com.epm.user.infrastructure.adapter.out.messaging;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

import com.epm.user.infrastructure.adapter.out.persistence.OutboxEventJpaEntity;
import com.epm.user.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes one batch of the outbox relay within its own {@link Transactional} transaction.
 *
 * <p>Separated from {@link OutboxRelayService} so that both the {@code @Scheduled}
 * trigger and the {@code @TransactionalEventListener} trigger call this bean
 * <em>cross-bean</em> — ensuring the Spring AOP proxy is honoured and
 * {@code @Transactional} is not bypassed via self-invocation.
 *
 * <p>Uses {@code FOR UPDATE SKIP LOCKED} queries to prevent duplicate processing
 * when multiple relay threads are active.
 */
@Component
public class OutboxRelayExecutor {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayExecutor.class);
    private static final long RETRY_COOLDOWN_MINUTES = 5;

    private final OutboxEventJpaRepository outboxRepo;
    private final KafkaOutboxPublisher publisher;

    public OutboxRelayExecutor(OutboxEventJpaRepository outboxRepo, KafkaOutboxPublisher publisher) {
        this.outboxRepo = outboxRepo;
        this.publisher = publisher;
    }

    /**
     * Fetches pending and retry-eligible outbox events (using {@code FOR UPDATE SKIP LOCKED}),
     * publishes each one to Kafka, and updates the row's {@code published_at} or
     * {@code failed_at} accordingly — all within a single transaction.
     */
    @Transactional
    public void relayBatch() {
        Instant cooldownThreshold = Instant.now().minus(RETRY_COOLDOWN_MINUTES, ChronoUnit.MINUTES);

        List<OutboxEventJpaEntity> pending = outboxRepo.lockPendingBatch();
        List<OutboxEventJpaEntity> retry = outboxRepo.lockRetryBatch(cooldownThreshold);

        // The two batch queries are disjoint by predicate (pending: failed_at IS NULL;
        // retry: failed_at IS NOT NULL), so no row can appear in both — concatenation
        // alone is correct and no .distinct() is needed.
        Stream.concat(pending.stream(), retry.stream())
                .forEach(this::publish);
    }

    // ── Private ──────────────────────────────────────────────────────────────

    private void publish(OutboxEventJpaEntity event) {
        try {
            publisher.publish(event.getTopic(), event.getAggregateId().toString(), event.getPayload());
            event.setPublishedAt(Instant.now());
            outboxRepo.save(event);
            log.debug("Relayed outbox event {} to topic {}", event.getId(), event.getTopic());
        } catch (Exception ex) {
            log.warn("Failed to relay outbox event {}: {}", event.getId(), ex.getMessage());
            event.setFailedAt(Instant.now());
            event.setError(ex.getMessage());
            outboxRepo.save(event);
        }
    }
}
