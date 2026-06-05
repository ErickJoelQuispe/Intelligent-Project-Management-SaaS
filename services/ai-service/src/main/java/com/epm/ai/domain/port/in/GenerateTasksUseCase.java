package com.epm.ai.domain.port.in;

import java.util.List;

import com.epm.ai.domain.model.TaskDraft;

/**
 * Inbound port: generate AI task drafts for a project.
 */
public interface GenerateTasksUseCase {

    /**
     * Generates a list of task drafts based on a description and project context.
     *
     * @param projectId   the target project identifier
     * @param userId      the user requesting generation
     * @param tenantId    the tenant identifier
     * @param description a free-form description of the work to be done
     * @param bypassCache when true, skips the cache and forces a fresh LLM call
     * @return list of generated task drafts
     */
    List<TaskDraft> execute(String projectId, String userId, String tenantId,
                            String description, boolean bypassCache);
}
