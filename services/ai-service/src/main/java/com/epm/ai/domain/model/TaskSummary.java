package com.epm.ai.domain.model;

/**
 * Lightweight projection of a task used to enrich the AI prompt with project context.
 */
public record TaskSummary(String title, String status) {}
