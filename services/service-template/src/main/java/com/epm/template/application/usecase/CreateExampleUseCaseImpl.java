package com.epm.template.application.usecase;

import java.util.UUID;

import com.epm.template.domain.model.Example;
import com.epm.template.domain.port.in.CreateExampleUseCase;
import com.epm.template.domain.port.out.TransactionalExampleWriter;

/**
 * Implementation of {@link CreateExampleUseCase}.
 *
 * <p>Orchestrates domain objects and driven ports.
 * No Spring annotations here — wiring happens in {@code infrastructure/config}.
 *
 * <p>The use case delegates to {@link TransactionalExampleWriter} which persists
 * the aggregate AND its domain events in a single transaction — the correct
 * outbox pattern. This eliminates the non-atomic save-then-publish anti-pattern
 * where a crash between the two calls would lose the event permanently.
 */
public class CreateExampleUseCaseImpl implements CreateExampleUseCase {

    private final TransactionalExampleWriter writer;

    public CreateExampleUseCaseImpl(TransactionalExampleWriter writer) {
        this.writer = writer;
    }

    @Override
    public Example create(UUID tenantId, String name) {
        Example example = Example.create(tenantId, name);
        return writer.saveAndPublish(example);
    }
}
