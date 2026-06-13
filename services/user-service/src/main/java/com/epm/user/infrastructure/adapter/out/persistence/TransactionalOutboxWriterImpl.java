package com.epm.user.infrastructure.adapter.out.persistence;

import java.util.List;

import com.epm.user.domain.model.Team;
import com.epm.user.domain.model.UserProfile;
import com.epm.user.domain.port.out.DomainEventPublisher;
import com.epm.user.domain.port.out.TransactionalOutboxWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Infrastructure implementation of {@link TransactionalOutboxWriter}.
 *
 * <p>Each method is annotated {@link Transactional} so aggregate persistence and
 * outbox event insertion happen atomically. A failure in either step rolls back both,
 * allowing a legitimate retry on the next delivery.
 *
 * <p>Domain events are pulled from the aggregate BEFORE the save (since the repository
 * re-fetches from the database after persisting, the in-memory event list would be lost
 * otherwise). The aggregate is then saved, and finally the already-pulled events are
 * published to the outbox table — all within the same transaction.
 */
@Component
public class TransactionalOutboxWriterImpl implements TransactionalOutboxWriter {

    private final TeamPersistenceAdapter teamRepository;
    private final UserProfilePersistenceAdapter profileRepository;
    private final DomainEventPublisher eventPublisher;

    public TransactionalOutboxWriterImpl(TeamPersistenceAdapter teamRepository,
            UserProfilePersistenceAdapter profileRepository,
            DomainEventPublisher eventPublisher) {
        this.teamRepository = teamRepository;
        this.profileRepository = profileRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Saves the team aggregate and publishes its domain events to the outbox
     * — all within one transaction. The aggregate is persisted first; events
     * are published after a successful save.
     */
    @Override
    @Transactional
    public Team saveTeamAndPublish(Team team) {
        // Pull events before save — TeamPersistenceAdapter re-fetches from DB,
        // which would discard the in-memory event list on the original aggregate.
        List<Object> events = team.pullDomainEvents();
        Team saved = teamRepository.save(team);
        eventPublisher.publish(events);
        return saved;
    }

    /**
     * Saves the user profile aggregate and publishes its domain events to the
     * outbox — all within one transaction.
     */
    @Override
    @Transactional
    public UserProfile saveProfileAndPublish(UserProfile profile) {
        List<Object> events = profile.pullDomainEvents();
        UserProfile saved = profileRepository.save(profile);
        eventPublisher.publish(events);
        return saved;
    }
}
