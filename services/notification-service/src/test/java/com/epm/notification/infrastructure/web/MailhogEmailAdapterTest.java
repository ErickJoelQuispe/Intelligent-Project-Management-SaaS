package com.epm.notification.infrastructure.web;

import java.util.Map;

import com.epm.notification.infrastructure.adapter.out.email.NoOpEmailAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for NoOpEmailAdapter (replaces MailhogEmailAdapterTest — T-C-07).
 *
 * <p>Verifies that the no-op adapter never throws and does not interact with any mail infrastructure.
 */
class MailhogEmailAdapterTest {

    private NoOpEmailAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new NoOpEmailAdapter();
    }

    // ── NoOpEmailAdapter never throws ──────────────────────────────────────

    @Test
    void send_withFullVars_doesNotThrow() {
        Map<String, Object> vars = Map.of(
                "assigneeName", "Alice",
                "taskTitle", "Fix login",
                "projectName", "Alpha",
                "taskUrl", "http://example.com/task/1");

        // Should complete silently without any exception
        adapter.send("alice@example.com", "Task Assigned", "task-assigned-v1", vars);
    }

    @Test
    void send_withEmptyVars_doesNotThrow() {
        adapter.send("user@example.com", "Subject", "any-template", Map.of());
    }
}
