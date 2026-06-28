package com.epm.ai.infrastructure.adapter.in.rest.dto;

/**
 * DTO representing a single conversation turn for the chat history payload.
 */
public record ChatTurn(String role, String content) {}
