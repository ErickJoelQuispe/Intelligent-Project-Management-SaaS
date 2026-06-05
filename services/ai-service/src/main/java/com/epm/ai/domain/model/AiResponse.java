package com.epm.ai.domain.model;

/**
 * Value object representing a response from the AI model.
 */
public record AiResponse(String content, int inputTokens, int outputTokens, String model, boolean cached) {}
