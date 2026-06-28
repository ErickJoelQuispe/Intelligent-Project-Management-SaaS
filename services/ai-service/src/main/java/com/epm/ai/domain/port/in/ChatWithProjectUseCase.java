package com.epm.ai.domain.port.in;

import java.util.Iterator;
import java.util.List;

import com.epm.ai.domain.model.AiResponse;
import com.epm.ai.domain.model.ChatTurn;
import com.epm.ai.domain.model.TaskSummary;

/**
 * Inbound port: chat with an AI assistant about a project.
 */
public interface ChatWithProjectUseCase {

    /**
     * Answers a user's question about the project using AI context.
     *
     * @param projectId     the project context identifier
     * @param userId        the user sending the chat message
     * @param tenantId      the tenant identifier
     * @param message       the user's message
     * @param history       previous conversation turns (may be empty, never null)
     * @param existingTasks current project tasks for context (may be empty, never null)
     * @return the AI response
     */
    AiResponse execute(String projectId, String userId, String tenantId, String message,
                       List<ChatTurn> history, List<TaskSummary> existingTasks);

    /**
     * Answers a user's question with streaming chunks of text.
     *
     * @param projectId     the project context identifier
     * @param userId        the user sending the chat message
     * @param tenantId      the tenant identifier
     * @param message       the user's message
     * @param history       previous conversation turns (may be empty, never null)
     * @param existingTasks current project tasks for context (may be empty, never null)
     * @return an iterator of text chunks
     */
    Iterator<String> executeStream(String projectId, String userId, String tenantId, String message,
                                   List<ChatTurn> history, List<TaskSummary> existingTasks);
}
