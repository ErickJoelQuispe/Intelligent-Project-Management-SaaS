package com.epm.template.domain.port.in;

import java.util.Optional;
import java.util.UUID;

import com.epm.template.domain.model.Example;

/**
 * Driving port: query side.
 *
 * <p>Keeping read and write use cases in separate interfaces follows the
 * Interface Segregation Principle and makes CQRS refactors straightforward.
 */
public interface FindExampleUseCase {

    Optional<Example> findById(UUID id);
}
