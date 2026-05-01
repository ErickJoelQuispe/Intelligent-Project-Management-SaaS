package com.epm.template.application.usecase;

import java.util.Optional;
import java.util.UUID;

import com.epm.template.domain.model.Example;
import com.epm.template.domain.port.in.FindExampleUseCase;
import com.epm.template.domain.port.out.ExampleRepository;

/**
 * Implementation of {@link FindExampleUseCase}.
 */
public class FindExampleUseCaseImpl implements FindExampleUseCase {

    private final ExampleRepository repository;

    public FindExampleUseCaseImpl(ExampleRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Example> findById(UUID id) {
        return repository.findById(id);
    }
}
