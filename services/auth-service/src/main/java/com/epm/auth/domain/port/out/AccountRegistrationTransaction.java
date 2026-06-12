package com.epm.auth.domain.port.out;

import com.epm.auth.domain.model.Account;

/**
 * Driven port: atomic persist-and-publish boundary for account registration.
 *
 * <p>Pure Java interface — no Spring or JPA imports. Implemented in the
 * infrastructure layer where {@code @Transactional} may be applied.
 *
 * <p>Implementations MUST persist the account and its pulled domain events
 * atomically: both writes share a single database transaction. A crash or
 * exception after the account row is saved but before the outbox row is
 * written must cause a full rollback so neither write is committed in isolation.
 *
 * @see com.epm.auth.infrastructure.adapter.out.persistence.TransactionalAccountRegistration
 */
public interface AccountRegistrationTransaction {

    /**
     * Persists the account and publishes its pending domain events in one atomic transaction.
     *
     * @param account the account to persist (domain events are pulled inside this method)
     * @return the persisted account (may have updated fields such as generated IDs)
     */
    Account saveAndPublish(Account account);
}
