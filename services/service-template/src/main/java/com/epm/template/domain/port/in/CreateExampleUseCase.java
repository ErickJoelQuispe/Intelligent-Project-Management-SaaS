package com.epm.template.domain.port.in;

import java.util.UUID;

import com.epm.template.domain.model.Example;

/**
 * Driving port: defines what this service can do from the outside world's perspective.
 *
 * <p>Controllers, event consumers, and CLI runners call this interface.
 * They never depend on the use case implementation directly.
 */
public interface CreateExampleUseCase {

    /**
     * Creates a new Example for the given tenant.
     *
     * @param tenantId the tenant this example belongs to
     * @param name     a non-blank display name for the example
     * @return the persisted Example aggregate
     */
    Example create(UUID tenantId, String name);
}
