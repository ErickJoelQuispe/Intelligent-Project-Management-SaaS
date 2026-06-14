package com.epm.project.infrastructure.adapter.out.persistence;

import java.util.List;

import com.epm.project.domain.model.Project;
import com.epm.project.domain.port.out.DomainEventPublisher;
import com.epm.project.domain.port.out.ProjectRepository;
import com.epm.project.domain.port.out.TransactionalOutboxWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Infrastructure implementation of {@link TransactionalOutboxWriter}.
 *
 * <p>Annotated {@link Transactional} so that aggregate persistence and outbox event
 * insertion happen atomically. A failure in either step rolls back both, allowing
 * a legitimate retry on the next delivery.
 *
 * <p><strong>Event ordering:</strong> domain events are pulled from the aggregate
 * BEFORE calling {@link ProjectRepository#save(Project)}. The
 * {@link ProjectPersistenceAdapter#save(Project)} implementation reconstitutes a fresh
 * aggregate by reloading from the database — the in-memory event list on the original
 * aggregate instance would therefore be lost after the save returns. Pulling first
 * ensures the captured events are published to the outbox within the same transaction.
 * This mirrors the pattern established in {@code user-service/TransactionalOutboxWriterImpl}.
 */
@Component
public class TransactionalOutboxWriterImpl implements TransactionalOutboxWriter {

    private final ProjectRepository projectRepository;
    private final DomainEventPublisher eventPublisher;

    public TransactionalOutboxWriterImpl(ProjectRepository projectRepository,
            DomainEventPublisher eventPublisher) {
        this.projectRepository = projectRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Saves the project aggregate and publishes its domain events to the outbox —
     * all within one transaction. Domain events are pulled BEFORE the save because
     * {@link ProjectPersistenceAdapter} reloads the aggregate from the database,
     * which would discard the in-memory event list on the original instance.
     *
     * @param project the aggregate to persist
     * @return the reloaded, persisted project
     */
    @Override
    @Transactional
    public Project saveAndPublish(Project project) {
        // Pull events before save — ProjectPersistenceAdapter.save() re-fetches from DB,
        // which would discard the in-memory event list on the original aggregate instance.
        List<Object> events = project.pullDomainEvents();
        Project saved = projectRepository.save(project);
        eventPublisher.publish(events);
        return saved;
    }
}
