package com.epm.ai.application.usecase;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.epm.ai.domain.event.AiTasksGenerated;
import com.epm.ai.domain.model.AiRequest;
import com.epm.ai.domain.model.AiResponse;
import com.epm.ai.domain.model.ProjectContext;
import com.epm.ai.domain.model.TaskDraft;
import com.epm.ai.domain.model.TaskPriority;
import com.epm.ai.domain.port.in.GenerateTasksUseCase;
import com.epm.ai.domain.port.out.AiCachePort;
import com.epm.ai.domain.port.out.AiEventPublisher;
import com.epm.ai.domain.port.out.AiModelPort;
import com.epm.ai.domain.port.out.AiTokenTracker;
import com.epm.ai.domain.port.out.ProjectContextPort;
import com.epm.ai.domain.service.CacheKeyGenerator;

/**
 * Application use case: generate AI task drafts for a project.
 *
 * <p>Flow:
 * <ol>
 *   <li>Fetch project context via {@link ProjectContextPort}</li>
 *   <li>Check cache (unless bypassCache=true)</li>
 *   <li>On cache miss: call {@link AiModelPort#generate}, cache the response,
 *       publish {@link AiTasksGenerated}, track tokens</li>
 *   <li>Parse JSON array from response content into {@link TaskDraft} list</li>
 * </ol>
 *
 * <p>Pure Java — no Spring, no infrastructure annotations.
 */
public class GenerateTasksUseCaseImpl implements GenerateTasksUseCase {

    private static final long CACHE_TTL_SECONDS = 3600L;
    private static final double COST_PER_1K_INPUT = 0.00014;
    private static final double COST_PER_1K_OUTPUT = 0.00028;

    private final AiModelPort modelPort;
    private final ProjectContextPort contextPort;
    private final AiEventPublisher eventPublisher;
    private final AiCachePort cachePort;
    private final AiTokenTracker tokenTracker;

    public GenerateTasksUseCaseImpl(AiModelPort modelPort,
                                    ProjectContextPort contextPort,
                                    AiEventPublisher eventPublisher,
                                    AiCachePort cachePort,
                                    AiTokenTracker tokenTracker) {
        this.modelPort = modelPort;
        this.contextPort = contextPort;
        this.eventPublisher = eventPublisher;
        this.cachePort = cachePort;
        this.tokenTracker = tokenTracker;
    }

    @Override
    public List<TaskDraft> execute(String projectId, String userId, String tenantId,
                                   String description, boolean bypassCache) {
        ProjectContext context = contextPort.fetchProjectContext(projectId, tenantId);
        String cacheKey = CacheKeyGenerator.generate("task-gen", projectId, description);

        if (!bypassCache) {
            Optional<AiResponse> cached = cachePort.get(cacheKey);
            if (cached.isPresent()) {
                return parseTaskDrafts(cached.get().content());
            }
        }

        String prompt = buildPrompt(context, description);
        AiRequest request = new AiRequest(prompt, projectId, userId, tenantId);
        AiResponse response = modelPort.generate(request);

        cachePort.put(cacheKey, response, CACHE_TTL_SECONDS);
        trackCost(response);

        List<TaskDraft> tasks = parseTaskDrafts(response.content());
        publishEvent(projectId, tenantId, userId, tasks);

        return tasks;
    }

    private String buildPrompt(ProjectContext context, String description) {
        return "Project: " + context.name() + "\n"
                + "Description: " + context.description() + "\n"
                + "Members: " + String.join(", ", context.memberNames()) + "\n"
                + "User request: " + description;
    }

    private void trackCost(AiResponse response) {
        double cost = (response.inputTokens() / 1000.0) * COST_PER_1K_INPUT
                + (response.outputTokens() / 1000.0) * COST_PER_1K_OUTPUT;
        tokenTracker.trackTokens(response.inputTokens(), response.outputTokens(), response.model(), cost);
    }

    private void publishEvent(String projectId, String tenantId, String userId, List<TaskDraft> tasks) {
        AiTasksGenerated event = new AiTasksGenerated(null, projectId, tasks, tenantId, userId, null);
        eventPublisher.publish(event);
    }

    /**
     * Parses a JSON array of task drafts from the model response content.
     *
     * <p>Expected format: {@code [{"title":"...","description":"...","priority":"HIGH|MEDIUM|LOW"},...]}
     * Uses lightweight manual parsing to avoid adding a JSON library dependency in the domain layer.
     */
    private List<TaskDraft> parseTaskDrafts(String json) {
        List<TaskDraft> tasks = new ArrayList<>();
        int i = 0;
        while (i < json.length()) {
            int start = json.indexOf('{', i);
            if (start == -1) break;
            int end = findClosingBrace(json, start);
            if (end == -1) break;
            String block = json.substring(start, end + 1);
            TaskDraft draft = parseTaskDraft(block);
            if (draft != null) {
                tasks.add(draft);
            }
            i = end + 1;
        }
        return tasks;
    }

    private int findClosingBrace(String json, int openPos) {
        int depth = 0;
        for (int i = openPos; i < json.length(); i++) {
            if (json.charAt(i) == '{') depth++;
            else if (json.charAt(i) == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private TaskDraft parseTaskDraft(String block) {
        String title = extractJsonString(block, "title");
        String description = extractJsonString(block, "description");
        String priorityStr = extractJsonString(block, "priority");
        if (title == null || description == null || priorityStr == null) {
            return null;
        }
        TaskPriority priority;
        try {
            priority = TaskPriority.valueOf(priorityStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            priority = TaskPriority.MEDIUM;
        }
        return new TaskDraft(title, description, priority);
    }

    private String extractJsonString(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIdx = json.indexOf(searchKey);
        if (keyIdx == -1) return null;
        int colonIdx = json.indexOf(':', keyIdx + searchKey.length());
        if (colonIdx == -1) return null;
        int quoteStart = json.indexOf('"', colonIdx + 1);
        if (quoteStart == -1) return null;
        int quoteEnd = quoteStart + 1;
        while (quoteEnd < json.length()) {
            if (json.charAt(quoteEnd) == '"' && json.charAt(quoteEnd - 1) != '\\') break;
            quoteEnd++;
        }
        if (quoteEnd >= json.length()) return null;
        return json.substring(quoteStart + 1, quoteEnd);
    }
}
