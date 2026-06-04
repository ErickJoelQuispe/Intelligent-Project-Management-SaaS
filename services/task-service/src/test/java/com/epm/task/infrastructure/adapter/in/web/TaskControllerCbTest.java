package com.epm.task.infrastructure.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.epm.task.domain.exception.ProjectServiceUnavailableException;
import com.epm.task.domain.port.in.AssignTaskUseCase;
import com.epm.task.domain.port.in.ChangeTaskStatusUseCase;
import com.epm.task.domain.port.in.CreateSubtaskUseCase;
import com.epm.task.domain.port.in.CreateTaskUseCase;
import com.epm.task.domain.port.in.DeleteTaskUseCase;
import com.epm.task.domain.port.in.UpdateTaskUseCase;
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
 * @WebMvcTest for circuit-breaker 503 scenario.
 *
 * <p>Spec scenarios covered (A-1):
 * <ul>
 *   <li>503 response MUST have Retry-After HTTP header (not JSON body field)</li>
 *   <li>503 JSON body MUST NOT contain retryAfter field</li>
 *   <li>Header value must be parseable as a positive integer</li>
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
class TaskControllerCbTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateTaskUseCase createTaskUseCase;

    @MockitoBean
    private CreateSubtaskUseCase createSubtaskUseCase;

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

    // ── Scenario A-1a: 503 has Retry-After HTTP header ───────────────────────

    @Test
    void circuitBreakerOpen_returns503_withRetryAfterHeader() throws Exception {
        when(createTaskUseCase.execute(any()))
                .thenThrow(new ProjectServiceUnavailableException("CB open"));

        mockMvc.perform(post("/api/v1/tasks")
                        .with(jwt().jwt(j -> j
                                .subject(callerId.toString())
                                .claim("tenant_id", tenantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "%s",
                                  "title": "Test Task",
                                  "priority": "HIGH"
                                }
                                """.formatted(projectId)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("Retry-After", "30"));
    }

    // ── Scenario A-1b: 503 JSON body MUST NOT contain retryAfter field ────────

    @Test
    void circuitBreakerOpen_bodyDoesNotContainRetryAfterField() throws Exception {
        when(createTaskUseCase.execute(any()))
                .thenThrow(new ProjectServiceUnavailableException("CB open"));

        mockMvc.perform(post("/api/v1/tasks")
                        .with(jwt().jwt(j -> j
                                .subject(callerId.toString())
                                .claim("tenant_id", tenantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "%s",
                                  "title": "Test Task",
                                  "priority": "HIGH"
                                }
                                """.formatted(projectId)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.properties.Retry-After").doesNotExist())
                .andExpect(jsonPath("$.properties.retryAfter").doesNotExist());
    }
}
