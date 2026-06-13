package com.epm.template.infrastructure.adapter.in.rest;

import java.util.UUID;

/**
 * Incoming DTO for example creation.
 *
 * <p>DTOs belong to the infrastructure layer. The domain never sees them.
 * The {@code tenantId} is provided by the client (or extracted from a JWT claim
 * in a production service with authentication middleware).
 */
record CreateExampleRequest(UUID tenantId, String name) {}
