package com.epm.ai.infrastructure.adapter.in.rest.dto;

import java.util.List;

import com.epm.ai.domain.model.TaskDraft;

/**
 * Response DTO for task generation.
 */
public record GenerateTasksResponse(List<TaskDraft> tasks, boolean cached) {}
