package com.epm.auth.infrastructure.adapter.out.messaging;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

import com.epm.auth.infrastructure.adapter.out.persistence.OutboxEventJpaEntity;
import com.epm.auth.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes a single transactional relay batch of outbox events.
 *
 * <p>This bean is deliberately SEPARATE from {@link OutboxRelayService} so that calls from
 * the service cross the Spring proxy boundary. Spring {@code @Transactional} is proxy-based:
 * a {@code this.}-style self-invocation inside the same bean bypasses the proxy and the
 * transaction never starts. By placing {@link #relayBatch()} on its own bean and invoking it
 * from the trigger service, the {@code @Transactional} advice is applied on BOTH the scheduled
 * and the event-driven paths.
 *
 * <h2>Double-publish prevention</h2>
 * <p>Because {@link #relayBatch()} is transactional, the {@code FOR UPDATE SKIP LOCKED} locks
 * acquired by {@link OutboxEventJpaRepository#lockPendingBatch()} and
 * {@link OutboxEventJpaRepository#lockRetryBatch} are held for the full relay cycle. Concurrent
 * invocations (scheduled + event-driven) skip rows already held by the other runner, ensuring
 * each outbox row is processed exactly once per relay window.
 */
@Component
public class OutboxRelayExecutor {

    private static final long RETRY_COOLDOWN_MINUTES = 5;

    private final OutboxEventJpaRepository outboxRepo;
    private final KafkaOutboxPublisher publisher;

    public OutboxRelayExecutor(OutboxEventJpaRepository outboxRepo, KafkaOutboxPublisher publisher) {
        this.outboxRepo = outboxRepo;
        this.publisher = publisher;
    }

    /**
     * Relays a single batch of pending and retry-eligible outbox events in one transaction.
     *
     * <p>{@code @Transactional} ensures the pessimistic {@code FOR UPDATE SKIP LOCKED} locks
     * from the repository queries span the entire relay cycle, preventing the scheduled run and
     * the event-driven run from grabbing the same rows concurrently. This method MUST be invoked
     * from a different bean for the transactional advice to apply.
     */
    @Transactional
    public void relayBatch() {
        Instant cooldownThreshold = Instant.now().minus(RETRY_COOLDOWN_MINUTES, ChronoUnit.MINUTES);

        List<OutboxEventJpaEntity> pending = outboxRepo.lockPendingBatch();
        List<OutboxEventJpaEntity> retry = outboxRepo.lockRetryBatch(cooldownThreshold);

        Stream.concat(pending.stream(), retry.stream())
                .distinct()
                .forEach(this::publish);
    }

    // ── Private ──────────────────────────────────────────────────────────────

    private void publish(OutboxEventJpaEntity event) {
        try {
            publisher.publish(event.getTopic(), event.getAggregateId().toString(), event.getPayload());
            event.setPublishedAt(Instant.now());
            outboxRepo.save(event);
        } catch (Exception ex) {
            event.setFailedAt(Instant.now());
            event.setError(ex.getMessage());
            outboxRepo.save(event);
        }
    }
}
