package com.epm.template.infrastructure.adapter.in.rest;

/**
 * Incoming DTO for example creation.
 *
 * <p>DTOs belong to the infrastructure layer. The domain never sees them.
 */
record CreateExampleRequest(String name) {}
