package com.epm.ai.domain.model;

/**
 * Represents a single turn in a conversation — either user or assistant.
 */
public record ChatTurn(String role, String content) {}
