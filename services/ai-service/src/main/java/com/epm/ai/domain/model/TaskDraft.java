package com.epm.ai.domain.model;

/**
 * Value object representing an AI-generated task draft pending user confirmation.
 */
public record TaskDraft(String title, String description, TaskPriority priority) {}
