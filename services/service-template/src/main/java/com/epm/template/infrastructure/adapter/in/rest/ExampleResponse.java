package com.epm.template.infrastructure.adapter.in.rest;

import java.util.UUID;

import com.epm.template.domain.model.Example;

/**
 * Outgoing DTO for example responses.
 *
 * <p>Mapping from domain to DTO lives here — not in the domain model.
 */
record ExampleResponse(UUID id, UUID tenantId, String name) {

    static ExampleResponse from(Example example) {
        return new ExampleResponse(example.id(), example.tenantId(), example.name());
    }
}
