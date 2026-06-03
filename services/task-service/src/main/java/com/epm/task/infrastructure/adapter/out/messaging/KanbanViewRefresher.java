package com.epm.task.infrastructure.adapter.out.messaging;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Refreshes the {@code task_kanban_view} materialized view after each task mutation.
 *
 * <p>Triggered via {@link TransactionalEventListener} after the outbox row is committed,
 * ensuring the view is never refreshed within an open transaction (which would block reads).
 * Uses {@code REFRESH MATERIALIZED VIEW CONCURRENTLY} so reads proceed during refresh.
 */
@Component
public class KanbanViewRefresher {

    private static final Logger log = LoggerFactory.getLogger(KanbanViewRefresher.class);

    private final EntityManager entityManager;

    public KanbanViewRefresher(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOutboxEventSaved(OutboxEventSavedEvent event) {
        try {
            entityManager.createNativeQuery(
                    "REFRESH MATERIALIZED VIEW CONCURRENTLY task_kanban_view").executeUpdate();
            log.debug("Refreshed task_kanban_view");
        } catch (Exception e) {
            log.warn("Failed to refresh task_kanban_view: {}", e.getMessage());
        }
    }
}
