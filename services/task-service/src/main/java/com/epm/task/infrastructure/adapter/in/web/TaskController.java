package com.epm.task.infrastructure.adapter.in.web;

import java.util.List;
import java.util.UUID;

import com.epm.task.domain.exception.InvalidStatusException;
import com.epm.task.domain.exception.TaskNotFoundException;
import com.epm.task.domain.model.Task;
import com.epm.task.domain.model.TaskPriority;
import com.epm.task.domain.model.TaskStatus;
import com.epm.task.domain.port.in.AssignTaskUseCase;
import com.epm.task.domain.port.in.ChangeTaskStatusUseCase;
import com.epm.task.domain.port.in.CreateSubtaskUseCase;
import com.epm.task.domain.port.in.CreateTaskUseCase;
import com.epm.task.domain.port.in.DeleteTaskUseCase;
import com.epm.task.domain.port.in.GetSubtasksUseCase;
import com.epm.task.domain.port.in.UpdateTaskUseCase;
import com.epm.task.domain.port.in.command.AssignTaskCommand;
import com.epm.task.domain.port.in.command.ChangeStatusCommand;
import com.epm.task.domain.port.in.command.CreateSubtaskCommand;
import com.epm.task.domain.port.in.command.CreateTaskCommand;
import com.epm.task.domain.port.in.command.UpdateTaskCommand;
import com.epm.task.domain.port.in.result.TaskResult;
import com.epm.task.domain.port.out.TaskRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for task CRUD operations.
 */
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final CreateTaskUseCase createTaskUseCase;
    private final CreateSubtaskUseCase createSubtaskUseCase;
    private final GetSubtasksUseCase getSubtasksUseCase;
    private final UpdateTaskUseCase updateTaskUseCase;
    private final ChangeTaskStatusUseCase changeTaskStatusUseCase;
    private final AssignTaskUseCase assignTaskUseCase;
    private final DeleteTaskUseCase deleteTaskUseCase;
    private final TaskRepository taskRepository;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    public TaskController(CreateTaskUseCase createTaskUseCase,
            CreateSubtaskUseCase createSubtaskUseCase,
            GetSubtasksUseCase getSubtasksUseCase,
            UpdateTaskUseCase updateTaskUseCase,
            ChangeTaskStatusUseCase changeTaskStatusUseCase,
            AssignTaskUseCase assignTaskUseCase,
            DeleteTaskUseCase deleteTaskUseCase,
            TaskRepository taskRepository,
            JwtClaimsExtractor jwtClaimsExtractor) {
        this.createTaskUseCase = createTaskUseCase;
        this.createSubtaskUseCase = createSubtaskUseCase;
        this.getSubtasksUseCase = getSubtasksUseCase;
        this.updateTaskUseCase = updateTaskUseCase;
        this.changeTaskStatusUseCase = changeTaskStatusUseCase;
        this.assignTaskUseCase = assignTaskUseCase;
        this.deleteTaskUseCase = deleteTaskUseCase;
        this.taskRepository = taskRepository;
        this.jwtClaimsExtractor = jwtClaimsExtractor;
    }

    /** POST /api/v1/tasks → 201 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse createTask(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateTaskRequest request) {
        UUID callerId = jwtClaimsExtractor.getUserId(jwt);
        UUID tenantId = jwtClaimsExtractor.getTenantId(jwt);
        TaskPriority priority = parsePriority(request.priority());
        TaskResult result = createTaskUseCase.execute(new CreateTaskCommand(
                tenantId,
                request.projectId(),
                callerId,
                request.title(),
                request.description(),
                priority,
                request.deadline(),
                request.assigneeId()));
        return TaskResponse.from(result);
    }

    /** POST /api/v1/tasks (with parentTaskId body field) handled via subtask endpoint */
    /** POST /api/v1/tasks/subtasks → 201 */
    @PostMapping("/subtasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse createSubtask(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateSubtaskRequest request) {
        UUID callerId = jwtClaimsExtractor.getUserId(jwt);
        UUID tenantId = jwtClaimsExtractor.getTenantId(jwt);
        TaskPriority priority = parsePriority(request.priority());
        TaskResult result = createSubtaskUseCase.execute(new CreateSubtaskCommand(
                tenantId,
                request.projectId(),
                request.parentTaskId(),
                callerId,
                request.title(),
                request.description(),
                priority,
                request.deadline(),
                request.assigneeId()));
        return TaskResponse.from(result);
    }

    /** GET /api/v1/tasks/{id} → 200 */
    @GetMapping("/{id}")
    public TaskResponse getTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        UUID tenantId = jwtClaimsExtractor.getTenantId(jwt);
        Task task = taskRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new TaskNotFoundException(id, tenantId));
        return TaskResponse.from(taskToResult(task));
    }

    /** GET /api/v1/tasks/{taskId}/subtasks → 200 */
    @GetMapping("/{taskId}/subtasks")
    @ResponseStatus(HttpStatus.OK)
    public List<TaskResponse> getSubtasks(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID taskId) {
        UUID tenantId = jwtClaimsExtractor.getTenantId(jwt);
        return getSubtasksUseCase.getSubtasks(taskId, tenantId).stream()
                .map(task -> TaskResponse.from(taskToResult(task)))
                .toList();
    }

    /** PUT /api/v1/tasks/{id} → 200 */
    @PutMapping("/{id}")
    public TaskResponse updateTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTaskRequest request) {
        UUID callerId = jwtClaimsExtractor.getUserId(jwt);
        UUID tenantId = jwtClaimsExtractor.getTenantId(jwt);
        TaskPriority priority = parsePriority(request.priority());
        TaskResult result = updateTaskUseCase.execute(new UpdateTaskCommand(
                id, tenantId, callerId,
                request.title(), request.description(),
                priority, request.deadline()));
        return TaskResponse.from(result);
    }

    /** PATCH /api/v1/tasks/{id}/status → 200 */
    @PatchMapping("/{id}/status")
    public TaskResponse changeStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody ChangeStatusRequest request) {
        UUID callerId = jwtClaimsExtractor.getUserId(jwt);
        UUID tenantId = jwtClaimsExtractor.getTenantId(jwt);
        TaskStatus status = parseStatus(request.status());
        TaskResult result = changeTaskStatusUseCase.execute(
                new ChangeStatusCommand(id, tenantId, callerId, status));
        return TaskResponse.from(result);
    }

    /** PATCH /api/v1/tasks/{id}/assignee → 200 */
    @PatchMapping("/{id}/assignee")
    public TaskResponse assignTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestBody AssignTaskRequest request) {
        UUID callerId = jwtClaimsExtractor.getUserId(jwt);
        UUID tenantId = jwtClaimsExtractor.getTenantId(jwt);
        TaskResult result = assignTaskUseCase.execute(
                new AssignTaskCommand(id, tenantId, callerId, request.assigneeId()));
        return TaskResponse.from(result);
    }

    /** DELETE /api/v1/tasks/{id} → 204 */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        UUID callerId = jwtClaimsExtractor.getUserId(jwt);
        UUID tenantId = jwtClaimsExtractor.getTenantId(jwt);
        deleteTaskUseCase.execute(id, tenantId, callerId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private TaskPriority parsePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return TaskPriority.MEDIUM;
        }
        try {
            return TaskPriority.valueOf(priority.toUpperCase());
        } catch (IllegalArgumentException e) {
            return TaskPriority.MEDIUM;
        }
    }

    private TaskStatus parseStatus(String status) {
        try {
            return TaskStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidStatusException(status);
        }
    }

    private TaskResult taskToResult(Task task) {
        return new TaskResult(
                task.getId(),
                task.getTenantId(),
                task.getProjectId(),
                task.getParentTaskId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getDeadline(),
                task.getAssigneeId(),
                task.getCreatedAt(),
                task.getUpdatedAt());
    }
}
