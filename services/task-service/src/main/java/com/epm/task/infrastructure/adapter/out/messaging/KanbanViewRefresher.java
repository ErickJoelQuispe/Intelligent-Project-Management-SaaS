package com.epm.task.infrastructure.adapter.out.messaging;

import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Refreshes the {@code task_kanban_view} materialized view on a 5-second schedule.
 *
 * <p><strong>Design</strong>: Switching to {@code @Scheduled(fixedDelay = 5000)} caps the
 * refresh rate at once per 5 s regardless of write volume, trading at most 5 s of Kanban
 * staleness for a dramatic throughput win.
 *
 * <p><strong>Why plain JDBC, not EntityManager</strong>:
 * {@code REFRESH MATERIALIZED VIEW CONCURRENTLY} cannot run inside a transaction block —
 * Postgres raises "ERROR: REFRESH MATERIALIZED VIEW CONCURRENTLY cannot run inside a
 * transaction block". {@code @Transactional} (even with REQUIRES_NEW) wraps the call in
 * a transaction, causing the refresh to fail silently. Using a raw JDBC connection with
 * auto-commit=true bypasses the transaction manager entirely.
 *
 * <p><strong>Overlap guard</strong>: {@code fixedDelay} waits for the refresh to complete
 * BEFORE starting the next 5-second wait, so a slow refresh never overlaps.
 */
@Component
public class KanbanViewRefresher {

    private static final Logger log = LoggerFactory.getLogger(KanbanViewRefresher.class);

    private final DataSource dataSource;

    public KanbanViewRefresher(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Refreshes the {@code task_kanban_view} every 5 seconds using a raw auto-commit
     * JDBC connection so the statement runs outside any transaction block.
     */
    @Scheduled(fixedDelay = 5000)
    public void refreshKanbanView() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(true);
            stmt.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY task_kanban_view");
            log.debug("Refreshed task_kanban_view");
        } catch (Exception e) {
            log.warn("Failed to refresh task_kanban_view: {}", e.getMessage());
        }
    }
}
