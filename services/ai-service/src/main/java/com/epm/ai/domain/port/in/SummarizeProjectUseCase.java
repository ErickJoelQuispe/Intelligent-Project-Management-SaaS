package com.epm.ai.domain.port.in;

/**
 * Inbound port: summarize a project using AI.
 */
public interface SummarizeProjectUseCase {

    /**
     * Generates a concise summary of the project's current state.
     *
     * @param projectId the target project identifier
     * @param userId    the user requesting the summary
     * @param tenantId  the tenant identifier
     * @return a summary string (at most 300 words)
     */
    String execute(String projectId, String userId, String tenantId);
}
