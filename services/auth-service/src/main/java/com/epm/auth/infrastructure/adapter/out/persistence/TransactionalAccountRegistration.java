package com.epm.auth.infrastructure.adapter.out.persistence;

import com.epm.auth.domain.model.Account;
import com.epm.auth.domain.port.out.AccountRegistrationTransaction;
import com.epm.auth.domain.port.out.AccountRepository;
import com.epm.auth.domain.port.out.DomainEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Infrastructure implementation of {@link AccountRegistrationTransaction}.
 *
 * <p>Annotated with {@code @Transactional} so that the account persistence
 * ({@link AccountRepository#save}) and the outbox insert ({@link DomainEventPublisher#publish})
 * share a single database transaction. Both writes commit or roll back together,
 * preventing the split-brain scenario where an account is saved but its outbox event is lost.
 *
 * <p>The {@link AccountRepository} and {@link DomainEventPublisher} ports are injected
 * here — in the infrastructure layer — keeping the application layer (use cases) free of
 * Spring and JPA imports.
 */
@Component
public class TransactionalAccountRegistration implements AccountRegistrationTransaction {

    private final AccountRepository accountRepository;
    private final DomainEventPublisher eventPublisher;

    public TransactionalAccountRegistration(
            AccountRepository accountRepository,
            DomainEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Persists the account and its domain events atomically.
     *
     * <p>Pulls domain events from the account after saving (so the saved version's ID
     * is used), then writes the outbox rows in the same transaction.
     *
     * @param account the account to persist
     * @return the saved account
     */
    @Override
    @Transactional
    public Account saveAndPublish(Account account) {
        Account saved = accountRepository.save(account);
        eventPublisher.publish(saved.pullDomainEvents());
        return saved;
    }
}
