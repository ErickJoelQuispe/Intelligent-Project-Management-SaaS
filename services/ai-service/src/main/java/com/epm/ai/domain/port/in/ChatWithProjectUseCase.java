package com.epm.ai.domain.port.in;

import com.epm.ai.domain.model.AiResponse;

/**
 * Inbound port: chat with an AI assistant about a project.
 * Returns the full response (SSE streaming is an infrastructure concern).
 */
public interface ChatWithProjectUseCase {

    /**
     * Answers a user's question about the project using AI context.
     *
     * @param projectId the project context identifier
     * @param userId    the user sending the chat message
     * @param tenantId  the tenant identifier
     * @param message   the user's message
     * @return the AI response
     */
    AiResponse execute(String projectId, String userId, String tenantId, String message);
}
