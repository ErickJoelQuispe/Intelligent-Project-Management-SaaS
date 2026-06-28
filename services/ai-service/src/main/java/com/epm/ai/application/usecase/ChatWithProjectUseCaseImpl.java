package com.epm.ai.application.usecase;

import java.util.Iterator;
import java.util.List;

import com.epm.ai.domain.model.AiRequest;
import com.epm.ai.domain.model.AiResponse;
import com.epm.ai.domain.model.ChatTurn;
import com.epm.ai.domain.model.ProjectContext;
import com.epm.ai.domain.model.TaskSummary;
import com.epm.ai.domain.port.in.ChatWithProjectUseCase;
import com.epm.ai.domain.port.out.AiModelPort;
import com.epm.ai.domain.port.out.AiTokenTracker;
import com.epm.ai.domain.port.out.ProjectContextPort;

/**
 * Application use case: chat with an AI assistant about a project.
 *
 * <p>Flow:
 * <ol>
 *   <li>Fetch project context via {@link ProjectContextPort}</li>
 *   <li>Build a context-enriched chat prompt including conversation history and task list</li>
 *   <li>Call {@link AiModelPort#chat} and return the full response</li>
 *   <li>Track token usage via {@link AiTokenTracker}</li>
 * </ol>
 *
 * <p>NOTE: SSE/streaming is an infrastructure concern handled by the controller layer.
 * This use case returns the complete response.
 *
 * <p>Pure Java — no Spring, no infrastructure annotations.
 */
public class ChatWithProjectUseCaseImpl implements ChatWithProjectUseCase {

    private static final double COST_PER_1K_INPUT = 0.00014;
    private static final double COST_PER_1K_OUTPUT = 0.00028;

    private final AiModelPort modelPort;
    private final ProjectContextPort contextPort;
    private final AiTokenTracker tokenTracker;

    public ChatWithProjectUseCaseImpl(AiModelPort modelPort,
                                      ProjectContextPort contextPort,
                                      AiTokenTracker tokenTracker) {
        this.modelPort = modelPort;
        this.contextPort = contextPort;
        this.tokenTracker = tokenTracker;
    }

    @Override
    public AiResponse execute(String projectId, String userId, String tenantId, String message,
                              List<ChatTurn> history, List<TaskSummary> existingTasks) {
        ProjectContext context = contextPort.fetchProjectContext(projectId, tenantId);
        String prompt = buildPrompt(context, message, history, existingTasks);
        AiRequest request = new AiRequest(prompt, projectId, userId, tenantId);
        AiResponse response = modelPort.chat(request);
        trackCost(response);
        return response;
    }

    @Override
    public Iterator<String> executeStream(String projectId, String userId, String tenantId, String message,
                                          List<ChatTurn> history, List<TaskSummary> existingTasks) {
        ProjectContext context = contextPort.fetchProjectContext(projectId, tenantId);
        String prompt = buildPrompt(context, message, history, existingTasks);
        AiRequest request = new AiRequest(prompt, projectId, userId, tenantId);
        return modelPort.chatStream(request);
    }

    private String buildPrompt(ProjectContext context, String userMessage,
                               List<ChatTurn> history, List<TaskSummary> existingTasks) {
        StringBuilder sb = new StringBuilder();
        sb.append("Project: ").append(context.name()).append("\n");
        sb.append("Description: ").append(context.description()).append("\n");
        sb.append("Members: ").append(String.join(", ", context.memberNames())).append("\n");

        if (existingTasks != null && !existingTasks.isEmpty()) {
            sb.append("\nCurrent project tasks:\n");
            for (TaskSummary task : existingTasks) {
                sb.append("- [").append(task.status()).append("] ").append(task.title()).append("\n");
            }
        }

        if (history != null && !history.isEmpty()) {
            sb.append("\nConversation so far:\n");
            for (ChatTurn turn : history) {
                String roleLabel = "user".equals(turn.role()) ? "User" : "Assistant";
                sb.append(roleLabel).append(": ").append(turn.content()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("User question: ").append(userMessage);
        return sb.toString();
    }

    private void trackCost(AiResponse response) {
        double cost = (response.inputTokens() / 1000.0) * COST_PER_1K_INPUT
                + (response.outputTokens() / 1000.0) * COST_PER_1K_OUTPUT;
        tokenTracker.trackTokens(response.inputTokens(), response.outputTokens(), response.model(), cost);
    }
}
