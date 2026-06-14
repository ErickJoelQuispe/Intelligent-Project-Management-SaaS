package com.epm.user.infrastructure.adapter.in.messaging;

import java.time.Instant;

import com.epm.user.infrastructure.adapter.out.persistence.ProcessedEventJpaEntity;
import com.epm.user.infrastructure.adapter.out.persistence.ProcessedEventJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inserts a {@code processed_events} claim row in its OWN {@code REQUIRES_NEW} transaction.
 *
 * <p>This bean exists so the claim INSERT has an independent transactional boundary that is
 * suspended/separate from the consumer's transaction. Crucially, it does <strong>NOT</strong>
 * catch {@link DataIntegrityViolationException}: a duplicate must PROPAGATE out of this
 * {@code REQUIRES_NEW} method so the inner transaction rolls back cleanly. The caller
 * ({@link IdempotencyGuard}) catches it outside any transaction, avoiding the
 * rollback-only poisoning that would occur if the catch were inside this boundary.
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
}
