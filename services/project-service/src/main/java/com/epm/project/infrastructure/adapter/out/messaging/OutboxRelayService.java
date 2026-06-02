package com.epm.project.infrastructure.adapter.out.messaging;

import java.time.Instant;
import java.util.List;

import com.epm.project.infrastructure.adapter.out.persistence.OutboxEventJpaEntity;
import com.epm.project.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Relays outbox events to Kafka after the producing transaction commits.
 *
 * <p>Two relay triggers:
 * <ol>
 *   <li>Immediate: {@link TransactionalEventListener} fires after commit of the
 *       transaction that saved the outbox row.</li>
 *   <li>Polling fallback: {@link Scheduled} every 5 s picks up any events missed
 *       by the immediate path (e.g., crash between save and relay).</li>
 * </ol>
 */
@Component
public class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);
    private static final long RETRY_THRESHOLD_MS = 30_000L;

    private final OutboxEventJpaRepository outboxRepo;
    private final KafkaOutboxPublisher publisher;

    public OutboxRelayService(OutboxEventJpaRepository outboxRepo, KafkaOutboxPublisher publisher) {
        this.outboxRepo = outboxRepo;
        this.publisher = publisher;
    }

    /** Fires immediately after the transaction that saved an outbox event commits. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutboxEventSaved(OutboxEventSavedEvent event) {
        relayPending();
    }

    /** Polling fallback — catches any events not relayed by the immediate path. */
    @Scheduled(fixedDelay = 5000)
    public void relayScheduled() {
        relayPending();
        retryFailed();
    }

    private void relayPending() {
        List<OutboxEventJpaEntity> pending =
                outboxRepo.findTop10ByPublishedAtIsNullAndFailedAtIsNullOrderByCreatedAtAsc();
        for (OutboxEventJpaEntity entity : pending) {
            log.debug("Relaying outbox event {} to topic {}", entity.getId(), entity.getTopic());
            publisher.publish(entity);
        }
    }

    private void retryFailed() {
        Instant threshold = Instant.now().minusMillis(RETRY_THRESHOLD_MS);
        List<OutboxEventJpaEntity> failed =
                outboxRepo.findTop10ByPublishedAtIsNullAndFailedAtBeforeOrderByCreatedAtAsc(threshold);
        for (OutboxEventJpaEntity entity : failed) {
            log.warn("Retrying failed outbox event {} to topic {}", entity.getId(), entity.getTopic());
            entity.setFailedAt(null);
            entity.setError(null);
            outboxRepo.save(entity);
            publisher.publish(entity);
        }
    }
}
