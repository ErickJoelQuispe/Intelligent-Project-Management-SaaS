package com.epm.ai.domain.model;

import java.util.List;

/**
 * Value object representing the context of a project used to prompt the AI model.
 */
public record ProjectContext(String projectId, String name, String description, List<String> memberNames) {}
