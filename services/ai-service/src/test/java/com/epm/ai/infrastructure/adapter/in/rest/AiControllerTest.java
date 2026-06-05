package com.epm.ai.infrastructure.adapter.in.rest;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.epm.ai.domain.exception.ProjectNotFoundException;
import com.epm.ai.domain.model.AiResponse;
import com.epm.ai.domain.model.TaskDraft;
import com.epm.ai.domain.model.TaskPriority;
import com.epm.ai.domain.port.in.ChatWithProjectUseCase;
import com.epm.ai.domain.port.in.GenerateTasksUseCase;
import com.epm.ai.domain.port.in.SummarizeProjectUseCase;
import com.epm.ai.infrastructure.config.SecurityConfig;
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
 * @WebMvcTest for {@link AiController}.
 *
 * <p>Uses valid UUID values for JWT subject and tenant_id to avoid
 * IllegalArgumentException from TenantInterceptor.
 *
 * <p>Scenarios:
 * <ul>
 *   <li>POST /api/v1/ai/tasks/generate → 200 with task list and cached indicator</li>
 *   <li>POST /api/v1/ai/tasks/generate → 401 without JWT</li>
 *   <li>POST /api/v1/ai/tasks/generate → 500 when use case throws RuntimeException</li>
 *   <li>POST /api/v1/ai/projects/{id}/summary → 404 when project not found</li>
 *   <li>POST /api/v1/ai/chat → 200 with chat response</li>
 *   <li>POST /api/v1/ai/tasks/generate → 400 when description is blank</li>
 * </ul>
 */
@WebMvcTest(AiController.class)
@Import({SecurityConfig.class, AiControllerAdvice.class})
@TestPropertySource(properties = {
    "spring.config.import=",
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "spring.security.oauth2.resourceserver.jwt.jwks-uri=https://example.com/.well-known/jwks.json",
    "eureka.client.enabled=false",
    "spring.ai.openai.api-key=test-key",
    "spring.ai.openai.base-url=https://api.deepseek.com"
})
class AiControllerTest {

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String TENANT_ID = "22222222-2222-2222-2222-222222222222";
    private static final String PROJECT_ID = "33333333-3333-3333-3333-333333333333";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GenerateTasksUseCase generateTasksUseCase;

    @MockitoBean
    private SummarizeProjectUseCase summarizeProjectUseCase;

    @MockitoBean
    private ChatWithProjectUseCase chatWithProjectUseCase;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    // ── 200: task generation with JWT ────────────────────────────────────────

    @Test
    void generateTasks_returns200_withTaskList() throws Exception {
        List<TaskDraft> drafts = List.of(
                new TaskDraft("Set up DB schema", "Create tables for user data", TaskPriority.HIGH),
                new TaskDraft("Write API docs", "OpenAPI documentation", TaskPriority.MEDIUM));
        when(generateTasksUseCase.execute(anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(drafts);

        mockMvc.perform(post("/api/v1/ai/tasks/generate")
                        .with(jwt().jwt(j -> j
                                .subject(USER_ID)
                                .claim("tenant_id", TENANT_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","description":"build a todo app"}
                                """.formatted(PROJECT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks").isArray())
                .andExpect(jsonPath("$.tasks[0].title").value("Set up DB schema"))
                .andExpect(jsonPath("$.tasks[1].title").value("Write API docs"))
                .andExpect(jsonPath("$.cached").value(false));
    }

    // ── 401: no JWT ──────────────────────────────────────────────────────────

    @Test
    void generateTasks_returns401_withoutJwt() throws Exception {
        mockMvc.perform(post("/api/v1/ai/tasks/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","description":"test"}
                                """.formatted(PROJECT_ID)))
                .andExpect(status().isUnauthorized());
    }

    // ── 500: use case throws RuntimeException ────────────────────────────────

    @Test
    void generateTasks_returns500_whenUseCaseThrows() throws Exception {
        when(generateTasksUseCase.execute(anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenThrow(new RuntimeException("AI service temporarily unavailable"));

        mockMvc.perform(post("/api/v1/ai/tasks/generate")
                        .with(jwt().jwt(j -> j
                                .subject(USER_ID)
                                .claim("tenant_id", TENANT_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","description":"build a todo app"}
                                """.formatted(PROJECT_ID)))
                .andExpect(status().isInternalServerError());
    }

    // ── 404: project not found ───────────────────────────────────────────────

    @Test
    void summarizeProject_returns404_whenProjectNotFound() throws Exception {
        when(summarizeProjectUseCase.execute(anyString(), anyString(), anyString()))
                .thenThrow(new ProjectNotFoundException("unknown-project"));

        mockMvc.perform(post("/api/v1/ai/projects/unknown-project/summary")
                        .with(jwt().jwt(j -> j
                                .subject(USER_ID)
                                .claim("tenant_id", TENANT_ID))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Project Not Found"));
    }

    // ── 200: chat response ───────────────────────────────────────────────────

    @Test
    void chat_returns200_withResponse() throws Exception {
        AiResponse aiResponse = new AiResponse("The project is on track.", 50, 20, "deepseek-chat", false);
        when(chatWithProjectUseCase.execute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(aiResponse);

        mockMvc.perform(post("/api/v1/ai/chat")
                        .with(jwt().jwt(j -> j
                                .subject(USER_ID)
                                .claim("tenant_id", TENANT_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","message":"How is the project?"}
                                """.formatted(PROJECT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("The project is on track."))
                .andExpect(jsonPath("$.cached").value(false));
    }

    // ── 400: missing required fields ─────────────────────────────────────────

    @Test
    void generateTasks_returns400_whenDescriptionBlank() throws Exception {
        mockMvc.perform(post("/api/v1/ai/tasks/generate")
                        .with(jwt().jwt(j -> j
                                .subject(USER_ID)
                                .claim("tenant_id", TENANT_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","description":""}
                                """.formatted(PROJECT_ID)))
                .andExpect(status().isBadRequest());
    }
}
