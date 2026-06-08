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
 */
@Component
class ExampleRepositoryAdapter implements ExampleRepository {

    private final ExampleJpaRepository jpaRepository;

    ExampleRepositoryAdapter(ExampleJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Example save(Example example) {
        ExampleJpaEntity entity = new ExampleJpaEntity(example.id(), example.name());
        ExampleJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Example> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    private Example toDomain(ExampleJpaEntity entity) {
        return new Example(entity.getId(), entity.getName());
    }
}
