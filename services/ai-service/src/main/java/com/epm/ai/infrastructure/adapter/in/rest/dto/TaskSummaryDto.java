package com.epm.ai.infrastructure.adapter.in.rest.dto;

/**
 * DTO for a project task summary sent by the frontend for AI context enrichment.
 */
public record TaskSummaryDto(String title, String status) {}
