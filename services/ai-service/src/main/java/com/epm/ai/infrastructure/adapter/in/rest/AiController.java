package com.epm.ai.infrastructure.adapter.in.rest;

import java.util.List;

import com.epm.ai.domain.model.AiResponse;
import com.epm.ai.domain.model.TaskDraft;
import com.epm.ai.domain.port.in.ChatWithProjectUseCase;
import com.epm.ai.domain.port.in.GenerateTasksUseCase;
import com.epm.ai.domain.port.in.SummarizeProjectUseCase;
import com.epm.ai.infrastructure.adapter.in.rest.dto.ChatRequest;
import com.epm.ai.infrastructure.adapter.in.rest.dto.ChatResponse;
import com.epm.ai.infrastructure.adapter.in.rest.dto.GenerateTasksRequest;
import com.epm.ai.infrastructure.adapter.in.rest.dto.GenerateTasksResponse;
import com.epm.ai.infrastructure.adapter.in.rest.dto.SummaryResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for AI operations: task generation, project summary, and chat.
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final GenerateTasksUseCase generateTasksUseCase;
    private final SummarizeProjectUseCase summarizeProjectUseCase;
    private final ChatWithProjectUseCase chatWithProjectUseCase;

    public AiController(GenerateTasksUseCase generateTasksUseCase,
                        SummarizeProjectUseCase summarizeProjectUseCase,
                        ChatWithProjectUseCase chatWithProjectUseCase) {
        this.generateTasksUseCase = generateTasksUseCase;
        this.summarizeProjectUseCase = summarizeProjectUseCase;
        this.chatWithProjectUseCase = chatWithProjectUseCase;
    }

    /**
     * POST /api/v1/ai/tasks/generate
     * Generates AI task drafts for a project.
     */
    @PostMapping("/tasks/generate")
    public ResponseEntity<GenerateTasksResponse> generateTasks(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody GenerateTasksRequest request) {
        String userId = jwt.getSubject();
        String tenantId = jwt.getClaimAsString("tenant_id");

        List<TaskDraft> tasks = generateTasksUseCase.execute(
                request.projectId(),
                userId,
                tenantId,
                request.description(),
                request.bypassCache());

        return ResponseEntity.ok(new GenerateTasksResponse(tasks, false));
    }

    /**
     * POST /api/v1/ai/projects/{id}/summary
     * Generates a concise AI summary of a project.
     */
    @PostMapping("/projects/{id}/summary")
    public ResponseEntity<SummaryResponse> summarizeProject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id) {
        String userId = jwt.getSubject();
        String tenantId = jwt.getClaimAsString("tenant_id");

        String summary = summarizeProjectUseCase.execute(id, userId, tenantId);
        return ResponseEntity.ok(new SummaryResponse(summary, false));
    }

    /**
     * POST /api/v1/ai/chat
     * Answers a user's question about a project.
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChatRequest request) {
        String userId = jwt.getSubject();
        String tenantId = jwt.getClaimAsString("tenant_id");

        AiResponse response = chatWithProjectUseCase.execute(
                request.projectId(),
                userId,
                tenantId,
                request.message());

        return ResponseEntity.ok(new ChatResponse(
                response.content(),
                response.cached(),
                response.model(),
                response.inputTokens(),
                response.outputTokens()));
    }
}
