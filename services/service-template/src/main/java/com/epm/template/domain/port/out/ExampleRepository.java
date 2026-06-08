package com.epm.template.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import com.epm.template.domain.model.Example;

/**
 * Driven port: persistence contract defined by the domain.
 *
 * <p>The domain owns this interface. Infrastructure provides the implementation.
 * This inversion is the heart of hexagonal architecture: the domain dictates
 * what it needs; it does not depend on how it is stored.
 */
public interface ExampleRepository {

    Example save(Example example);

    Optional<Example> findById(UUID id);
}
