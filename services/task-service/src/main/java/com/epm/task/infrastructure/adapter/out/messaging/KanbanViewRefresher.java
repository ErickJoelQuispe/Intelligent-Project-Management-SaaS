package com.epm.task.infrastructure.adapter.out.messaging;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refreshes the {@code task_kanban_view} materialized view on a 5-second schedule.
 *
 * <p><strong>Design</strong>: Previously this class refreshed the view on every
 * {@code OutboxEventSavedEvent} (every task write), which caused a full table scan across
 * ALL tenants on every single mutation — O(writes) full scans instead of O(time). Switching
 * to {@code @Scheduled(fixedDelay = 5000)} caps the refresh rate at once per 5 s regardless
 * of write volume, trading at most 5 s of Kanban staleness for a dramatic throughput win.
 *
 * <p><strong>Staleness window</strong>: Kanban board reads may be up to 5 s stale after a
 * task mutation. This is acceptable for a Kanban-style board; document this on the GET
 * /kanban endpoint Javadoc.
 *
 * <p><strong>Overlap guard</strong>: {@code fixedDelay} (not {@code fixedRate}) waits for
 * the refresh to complete BEFORE starting the 5-second wait, so a slow refresh (> 5 s) never
 * produces concurrent overlapping calls from this scheduler.
 *
 * <p>Uses {@code REFRESH MATERIALIZED VIEW CONCURRENTLY} so reads proceed unblocked during
 * refresh. Requires the unique index {@code idx_task_kanban_view} (created in V3 migration).
 */
@Component
public class KanbanViewRefresher {

    private static final Logger log = LoggerFactory.getLogger(KanbanViewRefresher.class);

    private final EntityManager entityManager;

    public KanbanViewRefresher(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Refreshes the {@code task_kanban_view} every 5 seconds.
     *
     * <p>Runs in its own {@code REQUIRES_NEW} transaction so the refresh does not
     * participate in any outer transaction context. A failure is logged and swallowed
     * so a temporary Postgres issue does not break the scheduler loop.
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refreshKanbanView() {
        try {
            entityManager.createNativeQuery(
                    "REFRESH MATERIALIZED VIEW CONCURRENTLY task_kanban_view").executeUpdate();
            log.debug("Refreshed task_kanban_view");
        } catch (Exception e) {
            log.warn("Failed to refresh task_kanban_view: {}", e.getMessage());
        }
    }
}
