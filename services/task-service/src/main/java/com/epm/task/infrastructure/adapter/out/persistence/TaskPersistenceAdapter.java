package com.epm.task.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.epm.task.domain.model.ActivityLog;
import com.epm.task.domain.model.PageResult;
import com.epm.task.domain.model.Task;
import com.epm.task.domain.model.TaskPriority;
import com.epm.task.domain.model.TaskStatus;
import com.epm.task.domain.port.out.ActivityLogRepository;
import com.epm.task.domain.port.out.TaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter implementing {@link TaskRepository} and {@link ActivityLogRepository} using JPA.
 */
@Component
public class TaskPersistenceAdapter implements TaskRepository, ActivityLogRepository {

    private final TaskJpaRepository taskJpaRepo;
    private final ActivityLogJpaRepository activityLogJpaRepo;

    public TaskPersistenceAdapter(TaskJpaRepository taskJpaRepo,
            ActivityLogJpaRepository activityLogJpaRepo) {
        this.taskJpaRepo = taskJpaRepo;
        this.activityLogJpaRepo = activityLogJpaRepo;
    }

    // ── TaskRepository ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Task save(Task task) {
        TaskJpaEntity entity = toJpa(task);
        taskJpaRepo.save(entity);
        return toDomain(entity);
    }

    @Override
    public Optional<Task> findByIdAndTenantId(UUID id, UUID tenantId) {
        return taskJpaRepo.findByIdAndTenantId(id, tenantId).map(this::toDomain);
    }

    @Override
    public PageResult<Task> findAllByProjectIdAndTenantId(UUID projectId, UUID tenantId, int page, int size) {
        Page<Task> springPage = taskJpaRepo
                .findAllByProjectIdAndTenantId(projectId, tenantId, PageRequest.of(page, size))
                .map(this::toDomain);
        return new PageResult<>(
                springPage.getContent(),
                springPage.getTotalElements(),
                springPage.getTotalPages(),
                springPage.getSize(),
                springPage.getNumber());
    }

    @Override
    public List<Task> findAllByProjectId(UUID projectId, UUID tenantId) {
        return taskJpaRepo.findAllByProjectIdAndTenantId(projectId, tenantId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Task> findSubtasksByParentId(UUID parentTaskId, UUID tenantId) {
        return taskJpaRepo.findSubtasksByParentIdAndTenantId(parentTaskId, tenantId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void deleteByIdAndTenantId(UUID id, UUID tenantId) {
        taskJpaRepo.deleteByIdAndTenantId(id, tenantId);
    }

    // ── ActivityLogRepository ─────────────────────────────────────────────────

    @Override
    @Transactional
    public ActivityLog save(ActivityLog log) {
        activityLogJpaRepo.save(toJpa(log));
        return log;
    }

    // ── Domain → JPA ─────────────────────────────────────────────────────────

    private TaskJpaEntity toJpa(Task task) {
        TaskJpaEntity entity = new TaskJpaEntity();
        entity.setId(task.getId());
        entity.setTenantId(task.getTenantId());
        entity.setProjectId(task.getProjectId());
        if (task.getParentTaskId() != null) {
            TaskJpaEntity parentRef = taskJpaRepo.getReferenceById(task.getParentTaskId());
            entity.setParentTask(parentRef);
        }
        entity.setTitle(task.getTitle());
        entity.setDescription(task.getDescription());
        entity.setStatus(task.getStatus());
        entity.setPriority(task.getPriority());
        entity.setDeadline(task.getDeadline());
        entity.setAssigneeId(task.getAssigneeId());
        entity.setCreatedAt(task.getCreatedAt() != null ? task.getCreatedAt() : Instant.now());
        entity.setUpdatedAt(task.getUpdatedAt() != null ? task.getUpdatedAt() : Instant.now());
        return entity;
    }

    private ActivityLogJpaEntity toJpa(ActivityLog log) {
        ActivityLogJpaEntity entity = new ActivityLogJpaEntity();
        entity.setId(log.getId());
        entity.setTaskId(log.getTaskId());
        entity.setTenantId(log.getTenantId());
        entity.setAction(log.getAction());
        entity.setActorId(log.getActorId());
        entity.setDetail(log.getDetail());
        entity.setCreatedAt(log.getCreatedAt() != null ? log.getCreatedAt() : Instant.now());
        return entity;
    }

    // ── JPA → Domain ─────────────────────────────────────────────────────────

    private Task toDomain(TaskJpaEntity entity) {
        UUID parentTaskId = entity.getParentTask() != null ? entity.getParentTask().getId() : null;
        return Task.reconstitute(
                entity.getId(),
                entity.getTenantId(),
                entity.getProjectId(),
                parentTaskId,
                entity.getTitle(),
                entity.getDescription(),
                TaskStatus.valueOf(entity.getStatus().name()),
                TaskPriority.valueOf(entity.getPriority().name()),
                entity.getDeadline(),
                entity.getAssigneeId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
