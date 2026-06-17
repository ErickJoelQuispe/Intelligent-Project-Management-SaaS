package com.epm.ai.infrastructure.adapter.out.messaging;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Triggers outbox relay operations after commits and on a polling schedule.
 *
 * <p>Two relay triggers:
 * <ol>
 *   <li>Immediate: {@link TransactionalEventListener} fires after commit of the
 *       transaction that saved the outbox row.</li>
 *   <li>Polling fallback: {@link Scheduled} every 5 s picks up any events missed
 *       by the immediate path (e.g., crash between save and relay).</li>
 * </ol>
 *
 * <p>The actual transactional work is delegated to {@link OutboxRelayExecutor}, a
 * separate {@code @Component}. This split is necessary because Spring AOP proxies
 * only intercept calls from <em>outside</em> the bean; calling
 * {@code this.relayPending()} from {@code relayScheduled()} on the same instance
 * would bypass the proxy and silently void the {@code REQUIRES_NEW} transaction and
 * {@code FOR UPDATE SKIP LOCKED} semantics.
 */
@Component
public class OutboxRelayService {

    private final OutboxRelayExecutor executor;

    public OutboxRelayService(OutboxRelayExecutor executor) {
        this.executor = executor;
    }

    /** Fires immediately after the transaction that saved an outbox event commits. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutboxEventSaved(OutboxEventSavedEvent event) {
        executor.relayPending();
    }

    /** Polling fallback — catches any events not relayed by the immediate path. */
    @Scheduled(fixedDelay = 5000)
    public void relayScheduled() {
        executor.relayPending();
        executor.retryFailed();
    }
}
