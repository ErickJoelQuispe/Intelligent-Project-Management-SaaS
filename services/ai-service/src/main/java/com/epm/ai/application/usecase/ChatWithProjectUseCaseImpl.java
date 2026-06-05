package com.epm.ai.application.usecase;

import com.epm.ai.domain.model.AiRequest;
import com.epm.ai.domain.model.AiResponse;
import com.epm.ai.domain.model.ProjectContext;
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
 *   <li>Build a context-enriched chat prompt</li>
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
    public AiResponse execute(String projectId, String userId, String tenantId, String message) {
        ProjectContext context = contextPort.fetchProjectContext(projectId, tenantId);
        String prompt = buildPrompt(context, message);
        AiRequest request = new AiRequest(prompt, projectId, userId, tenantId);
        AiResponse response = modelPort.chat(request);
        trackCost(response);
        return response;
    }

    private String buildPrompt(ProjectContext context, String userMessage) {
        return "Project: " + context.name() + "\n"
                + "Description: " + context.description() + "\n"
                + "Members: " + String.join(", ", context.memberNames()) + "\n"
                + "User question: " + userMessage;
    }

    private void trackCost(AiResponse response) {
        double cost = (response.inputTokens() / 1000.0) * COST_PER_1K_INPUT
                + (response.outputTokens() / 1000.0) * COST_PER_1K_OUTPUT;
        tokenTracker.trackTokens(response.inputTokens(), response.outputTokens(), response.model(), cost);
    }
}
