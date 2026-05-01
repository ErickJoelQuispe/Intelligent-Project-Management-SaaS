package com.epm.template.application.usecase;

import java.util.UUID;

import com.epm.template.domain.event.ExampleCreatedEvent;
import com.epm.template.domain.model.Example;
import com.epm.template.domain.port.in.CreateExampleUseCase;
import com.epm.template.domain.port.out.ExampleEventPublisher;
import com.epm.template.domain.port.out.ExampleRepository;

/**
 * Implementation of {@link CreateExampleUseCase}.
 *
 * <p>Orchestrates domain objects and driven ports.
 * No Spring annotations here — wiring happens in {@code infrastructure/config}.
 */
public class CreateExampleUseCaseImpl implements CreateExampleUseCase {

    private final ExampleRepository repository;
    private final ExampleEventPublisher eventPublisher;

    public CreateExampleUseCaseImpl(ExampleRepository repository, ExampleEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Example create(String name) {
        Example example = new Example(UUID.randomUUID(), name);
        Example saved = repository.save(example);
        eventPublisher.publish(ExampleCreatedEvent.of(saved.id(), saved.name()));
        return saved;
    }
}
