package com.epm.ai.infrastructure.adapter.in.rest.dto;

/**
 * Response DTO for AI chat.
 */
public record ChatResponse(String content, boolean cached, String model, int inputTokens, int outputTokens) {}
