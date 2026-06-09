package com.epm.task.infrastructure.adapter.in.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.epm.task.domain.model.PageResult;
import com.epm.task.domain.model.TaskStatus;
import com.epm.task.domain.port.in.GetTaskKanbanUseCase;
import com.epm.task.domain.port.in.ListTasksByProjectUseCase;
import com.epm.task.domain.port.in.result.KanbanResult;
import com.epm.task.domain.port.in.result.TaskResult;
import com.epm.task.domain.port.out.KanbanTaskRow;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for project-scoped task queries: list (paginated) and Kanban board.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/tasks")
public class KanbanController {

    private final ListTasksByProjectUseCase listTasksByProjectUseCase;
    private final GetTaskKanbanUseCase getTaskKanbanUseCase;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    public KanbanController(
            ListTasksByProjectUseCase listTasksByProjectUseCase,
            GetTaskKanbanUseCase getTaskKanbanUseCase,
            JwtClaimsExtractor jwtClaimsExtractor) {
        this.listTasksByProjectUseCase = listTasksByProjectUseCase;
        this.getTaskKanbanUseCase = getTaskKanbanUseCase;
        this.jwtClaimsExtractor = jwtClaimsExtractor;
    }

    /** GET /api/v1/projects/{projectId}/tasks → paginated list */
    @GetMapping
    public PageResult<TaskResponse> listTasksByProject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID tenantId = jwtClaimsExtractor.getTenantId(jwt);
        return listTasksByProjectUseCase.execute(projectId, tenantId, page, size).map(TaskResponse::from);
    }

    /** GET /api/v1/projects/{projectId}/tasks/kanban → Kanban board */
    @GetMapping("/kanban")
    public KanbanResponse getKanban(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        UUID tenantId = jwtClaimsExtractor.getTenantId(jwt);
        KanbanResult kanban = getTaskKanbanUseCase.execute(projectId, tenantId);

        Map<String, List<KanbanTaskSummary>> columns = new LinkedHashMap<>();
        for (TaskStatus status : TaskStatus.values()) {
            List<KanbanTaskRow> rows = kanban.columns().getOrDefault(status, List.of());
            columns.put(
                    status.name(),
                    rows.stream().map(KanbanTaskSummary::from).collect(Collectors.toList()));
        }
        return new KanbanResponse(columns);
    }
}
