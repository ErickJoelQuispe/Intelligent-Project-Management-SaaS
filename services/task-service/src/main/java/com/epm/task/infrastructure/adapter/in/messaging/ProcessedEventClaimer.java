package com.epm.task.infrastructure.adapter.in.messaging;

import java.time.Instant;

import com.epm.task.infrastructure.adapter.out.persistence.ProcessedEventJpaEntity;
import com.epm.task.infrastructure.adapter.out.persistence.ProcessedEventJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inserts and releases {@code processed_events} claim rows, each in their OWN
 * {@code REQUIRES_NEW} transaction.
 *
 * <p>This bean exists so each claim operation has an independent transactional boundary
 * suspended/separate from the consumer's transaction. Crucially, {@link #insertClaim}
 * does <strong>NOT</strong> catch {@link DataIntegrityViolationException}: a duplicate must
 * PROPAGATE out of this {@code REQUIRES_NEW} method so the inner transaction rolls back
 * cleanly. The caller ({@link IdempotencyGuard}) catches it outside any transaction,
 * avoiding the rollback-only poisoning that would occur if the catch were inside this boundary.
 *
 * <p>{@link #releaseClaim} is used by {@link com.epm.task.infrastructure.adapter.in.messaging.AiEventConsumer}
 * to compensate for an infrastructure failure mid-batch: the claim is deleted in its own
 * {@code REQUIRES_NEW} transaction so that Kafka redelivery can reprocess the event.
 */
@Component
public class ProcessedEventClaimer {

    private final ProcessedEventJpaRepository processedEventRepository;

    public ProcessedEventClaimer(ProcessedEventJpaRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    /**
     * Inserts the claim row. Commits on success; on a duplicate PK the
     * {@link DataIntegrityViolationException} propagates and this inner transaction rolls back.
     *
     * @param eventId the eventId to claim
     * @param topic   the source topic (stored for diagnostics)
     * @throws DataIntegrityViolationException if the eventId was already claimed
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertClaim(String eventId, String topic) {
        processedEventRepository.saveAndFlush(
                new ProcessedEventJpaEntity(eventId, topic, Instant.now()));
    }

    /**
     * Deletes the claim row (compensation). Runs in its own {@code REQUIRES_NEW} transaction
     * so it commits independently of any surrounding context (including a rolled-back one).
     *
     * <p>Used when an infrastructure failure occurs mid-batch in
     * {@link com.epm.task.infrastructure.adapter.in.messaging.AiEventConsumer}: if the claim
     * is NOT released, a redelivered event would be silently skipped even though no tasks were
     * successfully created. Releasing the claim allows the redelivery to reprocess the event.
     *
     * @param eventId the eventId whose claim should be released
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseClaim(String eventId) {
        // Explicit bulk DELETE, NOT deleteById: ProcessedEventJpaEntity reports isNew() == true
        // (to force INSERT-on-save for duplicate detection), which makes the standard
        // SimpleJpaRepository.delete() path a silent no-op. deleteByEventId bypasses that guard.
        processedEventRepository.deleteByEventId(eventId);
    }
}
