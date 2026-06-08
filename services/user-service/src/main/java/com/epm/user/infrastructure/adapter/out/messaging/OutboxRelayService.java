package com.epm.user.infrastructure.adapter.out.messaging;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

import com.epm.user.infrastructure.adapter.out.persistence.OutboxEventJpaEntity;
import com.epm.user.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Relays pending outbox events to Kafka.
 *
 * <p>Two triggers:
 * <ol>
 *   <li>{@link TransactionalEventListener} fires after a transaction commits (near real-time).</li>
 *   <li>{@link Scheduled} fires every 5 seconds as a safety net (catches missed events).</li>
 * </ol>
 *
 * <p>Failed events are retried after a 5-minute cooldown.
 */
@Service
public class OutboxRelayService {

    private static final long RETRY_COOLDOWN_MINUTES = 5;

    private final OutboxEventJpaRepository outboxRepo;
    private final KafkaOutboxPublisher publisher;

    public OutboxRelayService(OutboxEventJpaRepository outboxRepo, KafkaOutboxPublisher publisher) {
        this.outboxRepo = outboxRepo;
        this.publisher = publisher;
    }

    /**
     * Triggered after an outbox row is saved (post-commit).
     * Provides near-real-time relay without scheduler delay.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutboxEventSaved(OutboxEventSavedEvent event) {
        relayPendingEvents();
    }

    /**
     * Scheduled safety-net relay — catches any events missed by the event listener.
     */
    @Scheduled(fixedDelay = 5000)
    public void relayPendingEvents() {
        Instant cooldownThreshold = Instant.now().minus(RETRY_COOLDOWN_MINUTES, ChronoUnit.MINUTES);

        List<OutboxEventJpaEntity> pending =
                outboxRepo.findTop10ByPublishedAtIsNullAndFailedAtIsNullOrderByCreatedAtAsc();
        List<OutboxEventJpaEntity> retry =
                outboxRepo.findTop10ByPublishedAtIsNullAndFailedAtBeforeOrderByCreatedAtAsc(cooldownThreshold);

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
