package com.epm.user.infrastructure.adapter.out.messaging;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Triggers the outbox relay via two entry points.
 *
 * <p>Both triggers delegate to {@link OutboxRelayExecutor#relayBatch()} through
 * a cross-bean call so that Spring's AOP proxy honours the
 * {@code @Transactional} annotation on the executor — avoiding self-invocation
 * that would bypass the proxy and lose transaction semantics.
 *
 * <ol>
 *   <li>{@link TransactionalEventListener} fires after a transaction commits (near real-time).</li>
 *   <li>{@link Scheduled} fires every 5 seconds as a safety net (catches missed events).</li>
 * </ol>
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
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutboxEventSaved(OutboxEventSavedEvent event) {
        executor.relayBatch();
    }

    /**
     * Scheduled safety-net relay — catches any events missed by the event listener.
     */
    @Scheduled(fixedDelay = 5000)
    public void scheduledRelay() {
        executor.relayBatch();
    }
}
