package com.epm.template.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import com.epm.template.domain.model.Example;
import com.epm.template.domain.port.out.ExampleRepository;
import org.springframework.stereotype.Component;

/**
 * Persistence adapter: implements the domain port using JPA.
 *
 * <p>This class is the only bridge between the domain's {@link ExampleRepository}
 * interface and Spring Data JPA. If tomorrow we switch to jOOQ or JDBC,
 * only this class changes.
 *
 * <p>Note: {@link #save} re-fetches the entity from the database (via the JPA identity map
 * after {@code jpaRepository.save}), returning a fresh {@link Example} via
 * {@link Example#reconstitute} — which has an empty domain-event list. This is intentional:
 * domain events MUST be pulled from the aggregate BEFORE calling save (see
 * {@link com.epm.template.infrastructure.adapter.out.persistence.TransactionalExampleWriterImpl}).
 */
@Component
class ExampleRepositoryAdapter implements ExampleRepository {

    private final ExampleJpaRepository jpaRepository;

    ExampleRepositoryAdapter(ExampleJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Example save(Example example) {
        ExampleJpaEntity entity = new ExampleJpaEntity(example.id(), example.tenantId(), example.name());
        ExampleJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Example> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    private Example toDomain(ExampleJpaEntity entity) {
        return Example.reconstitute(entity.getId(), entity.getTenantId(), entity.getName());
    }
}
