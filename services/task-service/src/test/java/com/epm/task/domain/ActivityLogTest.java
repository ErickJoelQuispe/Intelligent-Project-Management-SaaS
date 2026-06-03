package com.epm.task.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.epm.task.domain.model.ActivityLog;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the ActivityLog entity.
 */
class ActivityLogTest {

    @Test
    void create_setsAllFields() {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        ActivityLog log = ActivityLog.create(taskId, tenantId, "STATUS_CHANGED", actorId);

        assertThat(log.getId()).isNotNull();
        assertThat(log.getTaskId()).isEqualTo(taskId);
        assertThat(log.getTenantId()).isEqualTo(tenantId);
        assertThat(log.getAction()).isEqualTo("STATUS_CHANGED");
        assertThat(log.getActorId()).isEqualTo(actorId);
        assertThat(log.getCreatedAt()).isNotNull();
    }

    @Test
    void create_withNullDetail_succeeds() {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        ActivityLog log = ActivityLog.create(taskId, tenantId, "ASSIGNED", actorId);

        assertThat(log.getDetail()).isNull();
    }
}
