package com.epm.template.infrastructure.adapter.out.persistence;

import java.util.List;

import com.epm.template.domain.model.Example;
import com.epm.template.domain.port.out.ExampleEventPublisher;
import com.epm.template.domain.port.out.ExampleRepository;
import com.epm.template.domain.port.out.TransactionalExampleWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Infrastructure implementation of {@link TransactionalExampleWriter}.
 *
 * <p>The method is annotated {@link Transactional} so aggregate persistence and
 * outbox event insertion happen atomically. A failure in either step rolls back
 * both, preventing ghost events and orphaned rows.
 *
 * <p>Domain events are pulled from the aggregate BEFORE the save (since
 * {@link ExampleRepositoryAdapter#save} re-constructs the domain object via
 * {@link Example#reconstitute}, which has an empty domain-event list — the in-memory
 * events on the original instance would be silently lost otherwise). Pull first,
 * then save, then publish the already-pulled events — all within this transaction.
 */
@Component
public class TransactionalExampleWriterImpl implements TransactionalExampleWriter {

    private final ExampleRepository repository;
    private final ExampleEventPublisher eventPublisher;

    public TransactionalExampleWriterImpl(ExampleRepository repository, ExampleEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Saves the Example aggregate and publishes its domain events to the outbox
     * — all within one transaction. Events are pulled before save to avoid
     * losing the in-memory event list after the repository reconstitutes the aggregate.
     */
    @Override
    @Transactional
    public Example saveAndPublish(Example example) {
        // Pull events BEFORE save — ExampleRepositoryAdapter.save returns a reconstituted
        // aggregate (via Example.reconstitute) that has no in-memory events. Pulling first
        // captures the events from the original instance before that reconstitution happens.
        List<Object> events = example.pullDomainEvents();
        Example saved = repository.save(example);
        eventPublisher.publish(events);
        return saved;
    }
}
