package com.epm.ai.domain.model;

/**
 * Value object representing an inbound request to the AI model.
 */
public record AiRequest(String prompt, String projectId, String userId, String tenantId) {}
