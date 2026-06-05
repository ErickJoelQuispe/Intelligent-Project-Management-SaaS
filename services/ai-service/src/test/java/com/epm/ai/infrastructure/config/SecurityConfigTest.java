package com.epm.ai.infrastructure.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epm.ai.domain.port.in.ChatWithProjectUseCase;
import com.epm.ai.domain.port.in.GenerateTasksUseCase;
import com.epm.ai.domain.port.in.SummarizeProjectUseCase;
import com.epm.ai.infrastructure.adapter.in.rest.AiController;
import com.epm.ai.infrastructure.adapter.in.rest.AiControllerAdvice;
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
 * Verifies SecurityConfig:
 * - /api/v1/ai/** requires JWT (401 without token)
 * - Valid UUID-format JWT claims allow access (not blocked by security)
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
class SecurityConfigTest {

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

    // ── 401 without JWT ──────────────────────────────────────────────────────

    @Test
    void generateTasks_returns401_withoutJwt() throws Exception {
        mockMvc.perform(post("/api/v1/ai/tasks/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","description":"test"}
                                """.formatted(PROJECT_ID)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void summarizeProject_returns401_withoutJwt() throws Exception {
        mockMvc.perform(post("/api/v1/ai/projects/" + PROJECT_ID + "/summary"))
                .andExpect(status().isUnauthorized());
    }

    // ── Security passes with valid JWT (not blocked by auth) ─────────────────

    @Test
    void generateTasks_allowsRequest_withValidJwt() throws Exception {
        // Use case returns null by default (not stubbed) → controller returns null tasks
        // Key assertion: NOT 401 (security passed through)
        mockMvc.perform(post("/api/v1/ai/tasks/generate")
                        .with(jwt().jwt(j -> j
                                .subject(USER_ID)
                                .claim("tenant_id", TENANT_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","description":"generate tasks"}
                                """.formatted(PROJECT_ID)))
                .andExpect(status().is2xxSuccessful());
    }
}
