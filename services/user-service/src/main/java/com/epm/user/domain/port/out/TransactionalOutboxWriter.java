package com.epm.user.domain.port.out;

import com.epm.user.domain.model.Team;
import com.epm.user.domain.model.UserProfile;

/**
 * Driven port: persists an aggregate and its pulled domain events atomically
 * (within one transaction) using the outbox pattern.
 *
 * <p>Implementations must save the aggregate first, then publish the events
 * — both operations commit or roll back together.
 */
public interface TransactionalOutboxWriter {

    /**
     * Saves the team aggregate and publishes its pending domain events in one transaction.
     *
     * @param team the team aggregate (events returned by {@code pullDomainEvents()} will be persisted)
     * @return the reloaded, persisted team
     */
    Team saveTeamAndPublish(Team team);

    /**
     * Saves the user profile aggregate and publishes its pending domain events in one transaction.
     *
     * @param profile the profile aggregate (events returned by {@code pullDomainEvents()} will be persisted)
     * @return the saved, persisted user profile
     */
    UserProfile saveProfileAndPublish(UserProfile profile);
}
