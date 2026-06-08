package com.epm.auth.domain.port.out;

import com.epm.auth.domain.model.SecurityEvent;

/**
 * Driven port: persistence contract for {@link SecurityEvent} entity.
 *
 * <p>Implemented by the infrastructure layer (JPA adapter).
 */
public interface SecurityEventRepository {

    /**
     * Persists a security event (immutable — insert only).
     *
     * @param event the security event to persist
     */
    void save(SecurityEvent event);
}
