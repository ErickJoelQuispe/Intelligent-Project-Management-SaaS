package com.epm.task.infrastructure.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.epm.task.domain.model.TaskPriority;
import com.epm.task.domain.model.TaskStatus;
import com.epm.task.domain.port.in.AssignTaskUseCase;
import com.epm.task.domain.port.in.ChangeTaskStatusUseCase;
import com.epm.task.domain.port.in.CreateSubtaskUseCase;
import com.epm.task.domain.port.in.CreateTaskUseCase;
import com.epm.task.domain.port.in.DeleteTaskUseCase;
import com.epm.task.domain.port.in.GetSubtasksUseCase;
import com.epm.task.domain.port.in.UpdateTaskUseCase;
import com.epm.task.domain.port.in.result.TaskResult;
import com.epm.task.domain.port.out.TaskRepository;
import com.epm.task.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * @WebMvcTest for {@link TaskController}.
 *
 * <p>Scenarios:
 * <ul>
 *   <li>POST /api/v1/tasks with valid JWT → 201</li>
 *   <li>POST /api/v1/tasks without tenantId in JWT → 400 (TenantRequiredException)</li>
 * </ul>
 */
@WebMvcTest(TaskController.class)
@Import({SecurityConfig.class, JwtClaimsExtractor.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
    "spring.config.import=",
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "spring.security.oauth2.resourceserver.jwt.jwks-uri=https://example.com/.well-known/jwks.json",
    "eureka.client.enabled=false"
})
class TaskControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateTaskUseCase createTaskUseCase;

    @MockitoBean
    private CreateSubtaskUseCase createSubtaskUseCase;

    @MockitoBean
    private GetSubtasksUseCase getSubtasksUseCase;

    @MockitoBean
    private UpdateTaskUseCase updateTaskUseCase;

    @MockitoBean
    private ChangeTaskStatusUseCase changeTaskStatusUseCase;

    @MockitoBean
    private AssignTaskUseCase assignTaskUseCase;

    @MockitoBean
    private DeleteTaskUseCase deleteTaskUseCase;

    @MockitoBean
    private TaskRepository taskRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final UUID callerId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    // ── POST /api/v1/tasks with valid JWT → 201 ──────────────────────────────

    @Test
    void createTask_returns201_withValidJwt() throws Exception {
        UUID taskId = UUID.randomUUID();
        TaskResult result = buildTaskResult(taskId, tenantId, projectId);
        when(createTaskUseCase.execute(any())).thenReturn(result);

        mockMvc.perform(post("/api/v1/tasks")
                        .with(jwt().jwt(j -> j
                                .subject(callerId.toString())
                                .claim("tenant_id", tenantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "%s",
                                  "title": "My Task",
                                  "priority": "HIGH"
                                }
                                """.formatted(projectId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("My Task"))
                .andExpect(jsonPath("$.status").value("TODO"));
    }

    // ── POST /api/v1/tasks without tenantId in JWT → 400 ─────────────────────

    @Test
    void createTask_returns400_whenTenantIdMissingFromJwt() throws Exception {
        // When tenantId claim is absent, TenantRequiredException is thrown by domain
        when(createTaskUseCase.execute(any()))
                .thenThrow(new com.epm.task.domain.exception.TenantRequiredException());

        mockMvc.perform(post("/api/v1/tasks")
                        .with(jwt().jwt(j -> j
                                .subject(callerId.toString())
                                // No tenant_id claim
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "%s",
                                  "title": "No Tenant Task",
                                  "priority": "MEDIUM"
                                }
                                """.formatted(projectId)))
                .andExpect(status().isBadRequest());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private TaskResult buildTaskResult(UUID taskId, UUID tenantId, UUID projectId) {
        return new TaskResult(
                taskId, tenantId, projectId, null,
                "My Task", null,
                TaskStatus.TODO, TaskPriority.HIGH,
                null, null,
                Instant.now(), Instant.now());
    }
}
