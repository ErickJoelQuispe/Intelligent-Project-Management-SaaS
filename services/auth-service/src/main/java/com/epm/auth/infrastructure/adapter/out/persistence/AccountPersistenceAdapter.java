package com.epm.auth.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.epm.auth.domain.model.Account;
import com.epm.auth.domain.model.AccountStatus;
import com.epm.auth.domain.model.Email;
import com.epm.auth.domain.port.out.AccountRepository;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing {@link AccountRepository} port using JPA.
 *
 * <p>Maps between {@link Account} domain object and {@link AccountJpaEntity}.
 * Lives in the infrastructure layer — domain model is kept pure.
 */
@Component
public class AccountPersistenceAdapter implements AccountRepository {

    private final AccountJpaRepository jpaRepository;

    public AccountPersistenceAdapter(AccountJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public boolean existsByEmail(Email email) {
        return jpaRepository.existsByEmailAndDeletedAtIsNull(email.value());
    }

    @Override
    public Account save(Account account) {
        AccountJpaEntity entity = toEntity(account);
        AccountJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private AccountJpaEntity toEntity(Account account) {
        AccountJpaEntity entity = new AccountJpaEntity();
        entity.setId(account.getId());
        entity.setTenantId(account.getTenantId());
        entity.setKeycloakUserId(account.getKeycloakUserId());
        entity.setEmail(account.getEmail().value());
        entity.setStatus(account.getStatus().name());
        entity.setFailedAttempts(account.getFailedAttempts());
        entity.setLastLoginAt(account.getLastLoginAt());
        entity.setCreatedAt(account.getCreatedAt() != null ? account.getCreatedAt() : Instant.now());
        entity.setUpdatedAt(account.getUpdatedAt() != null ? account.getUpdatedAt() : Instant.now());
        entity.setCreatedBy("system");
        entity.setUpdatedBy("system");
        entity.setDeletedAt(account.getDeletedAt());
        return entity;
    }

    private Account toDomain(AccountJpaEntity entity) {
        return Account.reconstitute(
                entity.getId(),
                entity.getTenantId(),
                entity.getKeycloakUserId(),
                entity.getEmail(),
                AccountStatus.valueOf(entity.getStatus()),
                entity.getFailedAttempts(),
                entity.getLastLoginAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                (int) entity.getVersion(),
                entity.getDeletedAt());
    }
}
