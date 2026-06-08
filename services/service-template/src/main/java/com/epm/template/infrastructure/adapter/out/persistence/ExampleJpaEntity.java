package com.epm.template.infrastructure.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code examples} table.
 *
 * <p>This is an infrastructure detail — the domain model ({@link com.epm.template.domain.model.Example})
 * knows nothing about this class. The adapter maps between them.
 */
@Entity
@Table(name = "examples")
class ExampleJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false)
    private String name;

    protected ExampleJpaEntity() {}

    ExampleJpaEntity(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    UUID getId() {
        return id;
    }

    String getName() {
        return name;
    }
}
