package com.epm.ai.infrastructure.adapter.in.rest;

import java.util.Iterator;
import java.util.List;

import com.epm.ai.domain.model.AiResponse;
import com.epm.ai.domain.model.ChatTurn;
import com.epm.ai.domain.model.TaskDraft;
import com.epm.ai.domain.model.TaskSummary;
import com.epm.ai.domain.port.in.ChatWithProjectUseCase;
import com.epm.ai.domain.port.in.GenerateTasksUseCase;
import com.epm.ai.domain.port.in.SummarizeProjectUseCase;
import com.epm.ai.infrastructure.adapter.in.rest.dto.ChatRequest;
import com.epm.ai.infrastructure.adapter.in.rest.dto.ChatResponse;
import com.epm.ai.infrastructure.adapter.in.rest.dto.GenerateTasksRequest;
import com.epm.ai.infrastructure.adapter.in.rest.dto.GenerateTasksResponse;
import com.epm.ai.infrastructure.adapter.in.rest.dto.SummaryResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * REST adapter for AI operations: task generation, project summary, and chat.
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final GenerateTasksUseCase generateTasksUseCase;
    private final SummarizeProjectUseCase summarizeProjectUseCase;
    private final ChatWithProjectUseCase chatWithProjectUseCase;
    private final TaskExecutor taskExecutor;

    public AiController(GenerateTasksUseCase generateTasksUseCase,
                        SummarizeProjectUseCase summarizeProjectUseCase,
                        ChatWithProjectUseCase chatWithProjectUseCase,
                        @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.generateTasksUseCase = generateTasksUseCase;
        this.summarizeProjectUseCase = summarizeProjectUseCase;
        this.chatWithProjectUseCase = chatWithProjectUseCase;
        this.taskExecutor = taskExecutor;
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

        List<String> existing = request.existingTaskTitles() != null
                ? request.existingTaskTitles()
                : List.of();

        List<TaskDraft> tasks = generateTasksUseCase.execute(
                request.projectId(),
                userId,
                tenantId,
                request.description(),
                request.bypassCache(),
                existing);

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

        List<ChatTurn> history = mapHistory(request);
        List<TaskSummary> tasks = mapTasks(request);

        AiResponse response = chatWithProjectUseCase.execute(
                request.projectId(),
                userId,
                tenantId,
                request.message(),
                history,
                tasks);

        return ResponseEntity.ok(new ChatResponse(
                response.content(),
                response.cached(),
                response.model(),
                response.inputTokens(),
                response.outputTokens()));
    }

    /**
     * POST /api/v1/ai/chat/stream
     * SSE streaming chat endpoint. Returns tokens as they arrive from DeepSeek.
     */
    @PostMapping("/chat/stream")
    public SseEmitter chatStream(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChatRequest request) {
        String userId = jwt.getSubject();
        String tenantId = jwt.getClaimAsString("tenant_id");

        List<ChatTurn> history = mapHistory(request);
        List<TaskSummary> tasks = mapTasks(request);

        SseEmitter emitter = new SseEmitter(30000L);

        // Capture the SecurityContext from the request thread before handing off
        // to the executor. SecurityContextHolder uses ThreadLocal by default, so
        // the context is NOT propagated automatically to worker threads.
        SecurityContext securityContext = SecurityContextHolder.getContext();

        taskExecutor.execute(() -> {
            SecurityContextHolder.setContext(securityContext);
            try {
                Iterator<String> chunks = chatWithProjectUseCase.executeStream(
                        request.projectId(), userId, tenantId, request.message(), history, tasks);
                while (chunks.hasNext()) {
                    String chunk = chunks.next();
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data(chunk, MediaType.TEXT_PLAIN));
                }
                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            } finally {
                SecurityContextHolder.clearContext();
            }
        });

        return emitter;
    }

    // ── Mapping helpers ────────────────────────────────────────────────────

    private List<ChatTurn> mapHistory(ChatRequest request) {
        if (request.history() == null || request.history().isEmpty()) {
            return List.of();
        }
        return request.history().stream()
                .map(dto -> new ChatTurn(dto.role(), dto.content()))
                .toList();
    }

    private List<TaskSummary> mapTasks(ChatRequest request) {
        if (request.existingTasks() == null || request.existingTasks().isEmpty()) {
            return List.of();
        }
        return request.existingTasks().stream()
                .map(dto -> new TaskSummary(dto.title(), dto.status()))
                .toList();
    }
}
