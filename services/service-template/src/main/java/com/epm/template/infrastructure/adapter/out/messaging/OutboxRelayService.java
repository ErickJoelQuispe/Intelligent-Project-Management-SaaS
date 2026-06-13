package com.epm.template.infrastructure.adapter.out.messaging;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Triggers the outbox relay via two entry points.
 *
 * <p>Both triggers delegate to {@link OutboxRelayExecutor#relayBatch()} through a
 * <em>cross-bean</em> call — NOT a {@code this.*} self-invocation — so that Spring's
 * AOP proxy honours the {@code @Transactional} annotation on the executor.
 *
 * <p><strong>The self-invocation pitfall (why two classes are required):</strong>
 * If {@code scheduledRelay()} and {@code onOutboxEventSaved()} lived in the same
 * class as {@code relayBatch()}, calling {@code this.relayBatch()} would bypass the
 * Spring proxy entirely. {@code @Transactional} would be silently ignored, the
 * {@code FOR UPDATE SKIP LOCKED} database locks would be released immediately after
 * the query (not at transaction end), and concurrent relay threads would pick up the
 * same outbox rows — producing duplicate Kafka messages. Separating the executor into
 * its own {@code @Component} forces the call to go through the proxy, preserving the
 * transactional lock scope for the full batch.
 *
 * <p>Two triggers are used:
 * <ol>
 *   <li>{@link TransactionalEventListener} ({@code AFTER_COMMIT}) — fires after an outbox
 *       row is committed, providing near-real-time relay with no scheduler delay.</li>
 *   <li>{@link Scheduled} ({@code fixedDelay = 5000ms}) — safety-net that catches any
 *       events missed by the event listener (e.g. the listener fired but Kafka was down).</li>
 * </ol>
 */
@Service
public class OutboxRelayService {

    private final OutboxRelayExecutor executor;

    public OutboxRelayService(OutboxRelayExecutor executor) {
        this.executor = executor;
    }

    /**
     * Triggered after an outbox row is committed to the database (post-commit).
     * Provides near-real-time relay without waiting for the scheduled interval.
     *
     * @param event the Spring ApplicationEvent fired by the outbox publisher
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutboxEventSaved(OutboxEventSavedEvent event) {
        executor.relayBatch();
    }

    /**
     * Scheduled safety-net relay — catches any events missed by the event listener.
     * Runs every 5 seconds regardless of new outbox writes.
     */
    @Scheduled(fixedDelay = 5000)
    public void scheduledRelay() {
        executor.relayBatch();
    }
}
