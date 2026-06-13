package com.epm.template.infrastructure.adapter.out.messaging;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

import com.epm.template.infrastructure.adapter.out.persistence.OutboxEventJpaEntity;
import com.epm.template.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes one batch of the outbox relay within its own {@link Transactional} transaction.
 *
 * <p><strong>Why is this a separate bean from {@link OutboxRelayService}?</strong>
 * Spring's {@code @Transactional} is implemented via a JDK/CGLIB proxy: the annotation
 * only takes effect when a method is called through the Spring proxy, not when called
 * directly from inside the same object ({@code this.relayBatch()}). If
 * {@code OutboxRelayService} called {@code this.relayBatch()} (self-invocation), the
 * proxy would be bypassed, {@code @Transactional} would be silently ignored, and the
 * {@code FOR UPDATE SKIP LOCKED} locks would be released immediately instead of being
 * held for the duration of the batch — allowing concurrent relay threads to pick up the
 * same rows and produce duplicate Kafka messages.
 *
 * <p>By placing {@link #relayBatch()} in a separate {@code @Component}, both the
 * {@code @Scheduled} trigger and the {@code @TransactionalEventListener} trigger in
 * {@code OutboxRelayService} call it <em>cross-bean</em> — through the proxy — so
 * {@code @Transactional} is honoured correctly.
 *
 * <p>Uses {@code FOR UPDATE SKIP LOCKED} queries to prevent duplicate processing
 * when multiple relay threads or pods are active.
 */
@Component
public class OutboxRelayExecutor {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayExecutor.class);

    /**
     * Cool-down period before a failed event is eligible for retry.
     * Events with {@code failed_at} older than this threshold are re-attempted.
     */
    static final long RETRY_COOLDOWN_MINUTES = 5;

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
     *
     * <p>The two batch queries are disjoint by predicate:
     * pending uses {@code failed_at IS NULL}; retry uses {@code failed_at IS NOT NULL}.
     * No row can appear in both — concatenation is correct and no {@code .distinct()} is needed.
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
                .forEach(this::publishOne);
    }

    // ── Private ──────────────────────────────────────────────────────────────

    private void publishOne(OutboxEventJpaEntity event) {
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
