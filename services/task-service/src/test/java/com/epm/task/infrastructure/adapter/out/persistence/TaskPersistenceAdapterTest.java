package com.epm.task.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.epm.task.domain.model.Task;
import com.epm.task.domain.model.TaskPriority;
import com.epm.task.domain.model.TaskStatus;
import com.epm.task.domain.port.in.command.CreateTaskCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration test for {@link TaskPersistenceAdapter} — uses real PostgreSQL via Testcontainers.
 *
 * <p>Verifies W-02 (cross-tenant isolation): a task saved for tenantA is NOT visible to tenantB.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TaskPersistenceAdapter.class})
@TestPropertySource(properties = {
    "spring.config.import=",
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "eureka.client.enabled=false"
})
class TaskPersistenceAdapterTest {

    @Autowired
    private TaskPersistenceAdapter adapter;

    @Autowired
    private TaskJpaRepository taskJpaRepo;

    @Autowired
    private ActivityLogJpaRepository activityLogJpaRepo;

    // ── save + findByIdAndTenantId ────────────────────────────────────────────

    @Test
    void save_and_findByIdAndTenantId_returnsSavedTask() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();

        Task task = Task.create(new CreateTaskCommand(
                tenantId, projectId, callerId,
                "Integration Test Task", null,
                TaskPriority.HIGH, null, null));

        Task saved = adapter.save(task);

        Optional<Task> found = adapter.findByIdAndTenantId(saved.getId(), tenantId);

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Integration Test Task");
        assertThat(found.get().getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(found.get().getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(found.get().getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void findByIdAndTenantId_returnsEmpty_whenMissing() {
        Optional<Task> found = adapter.findByIdAndTenantId(UUID.randomUUID(), UUID.randomUUID());
        assertThat(found).isEmpty();
    }

    // ── W-02: cross-tenant isolation ─────────────────────────────────────────

    @Test
    void findAllByProjectIdAndTenantId_isolatesTenants_W02() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();

        // Seed 3 tasks for tenantA
        for (int i = 0; i < 3; i++) {
            Task task = Task.create(new CreateTaskCommand(
                    tenantA, projectId, callerId,
                    "TenantA Task " + i, null,
                    TaskPriority.MEDIUM, null, null));
            adapter.save(task);
        }

        // Query by tenantB — must return 0 results
        List<Task> results = adapter.findAllByProjectId(projectId, tenantB);

        assertThat(results).isEmpty();
    }

    @Test
    void findAllByProjectId_returnsTasks_forCorrectTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();

        // Seed 2 tasks for this tenant+project
        for (int i = 0; i < 2; i++) {
            Task task = Task.create(new CreateTaskCommand(
                    tenantId, projectId, callerId,
                    "Task " + i, null,
                    TaskPriority.LOW, null, null));
            adapter.save(task);
        }

        List<Task> results = adapter.findAllByProjectId(projectId, tenantId);

        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(t -> assertThat(t.getTenantId()).isEqualTo(tenantId));
    }
}
