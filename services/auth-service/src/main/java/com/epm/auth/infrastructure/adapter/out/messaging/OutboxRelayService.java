package com.epm.auth.infrastructure.adapter.out.messaging;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Triggers outbox relay cycles via two paths and delegates the actual work to
 * {@link OutboxRelayExecutor}.
 *
 * <p>Two triggers:
 * <ol>
 *   <li>{@link TransactionalEventListener} fires after a transaction commits (near real-time).</li>
 *   <li>{@link Scheduled} fires every 5 seconds as a safety net (catches missed events).</li>
 * </ol>
 *
 * <h2>Why a separate executor bean</h2>
 * <p>Spring {@code @Transactional} is proxy-based. If the transactional relay logic lived on
 * this same bean and were invoked via a {@code this.} self-call (as happened previously on the
 * post-commit path), the proxy would be bypassed and the transaction would NOT start — releasing
 * the {@code FOR UPDATE SKIP LOCKED} locks immediately and reintroducing the double-publish race.
 * Both triggers here call {@link OutboxRelayExecutor#relayBatch()}, a CROSS-BEAN call through the
 * proxy, so {@code @Transactional} is honored on BOTH paths.
 */
@Service
public class OutboxRelayService {

    private final OutboxRelayExecutor executor;

    public OutboxRelayService(OutboxRelayExecutor executor) {
        this.executor = executor;
    }

    /**
     * Triggered after an outbox row is saved (post-commit).
     * Provides near-real-time relay without scheduler delay.
     *
     * <p>Delegates to {@link OutboxRelayExecutor#relayBatch()} so the transactional advice applies.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutboxEventSaved(OutboxEventSavedEvent event) {
        executor.relayBatch();
    }

    /**
     * Scheduled safety-net relay — catches any events missed by the event listener.
     *
     * <p>Delegates to {@link OutboxRelayExecutor#relayBatch()} so the transactional advice applies.
     */
    @Scheduled(fixedDelay = 5000)
    public void scheduledRelay() {
        executor.relayBatch();
    }
}
