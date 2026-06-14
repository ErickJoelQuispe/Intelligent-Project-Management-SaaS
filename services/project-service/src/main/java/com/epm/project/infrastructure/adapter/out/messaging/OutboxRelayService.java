package com.epm.project.infrastructure.adapter.out.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Thin relay trigger: delegates actual relay work to {@link OutboxRelayExecutor}.
 *
 * <p>Two relay triggers are registered here:
 * <ol>
 *   <li>Immediate: {@link TransactionalEventListener} fires after the transaction
 *       that saved an outbox row commits (fast path).</li>
 *   <li>Polling fallback: {@link Scheduled} every 5 s picks up events missed by
 *       the immediate path (e.g., process crash between save and relay).</li>
 * </ol>
 *
 * <p><strong>Why a separate executor bean?</strong> If {@code relayBatch()} were a
 * private method on this class and called from {@code onOutboxEventSaved()} or
 * {@code relayScheduled()}, Spring AOP would not intercept the call (self-invocation
 * bypasses the proxy). That would mean {@code @Transactional} on the relay logic is
 * silently skipped, the {@code FOR UPDATE SKIP LOCKED} row locks are released
 * immediately, and concurrent scheduler runs can double-publish the same event.
 * Calling {@link OutboxRelayExecutor#relayBatch()} cross-bean ensures the proxy is
 * always honoured.
 */
@Component
public class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);

    private final OutboxRelayExecutor executor;

    public OutboxRelayService(OutboxRelayExecutor executor) {
        this.executor = executor;
    }

    /**
     * Fires immediately after the transaction that saved an outbox event commits.
     * Delegates to {@link OutboxRelayExecutor#relayBatch()} cross-bean so that
     * {@code @Transactional} on the executor is honoured.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutboxEventSaved(OutboxEventSavedEvent event) {
        log.debug("Outbox event saved — triggering immediate relay");
        executor.relayBatch();
    }

    /**
     * Polling fallback — catches events not relayed by the immediate path.
     * Delegates cross-bean to {@link OutboxRelayExecutor#relayBatch()}.
     */
    @Scheduled(fixedDelay = 5000)
    public void relayScheduled() {
        executor.relayBatch();
    }
}
