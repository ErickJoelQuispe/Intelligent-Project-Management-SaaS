package com.epm.ai.infrastructure.adapter.in.rest.dto;

/**
 * Response DTO for project summarization.
 */
public record SummaryResponse(String summary, boolean cached) {}
