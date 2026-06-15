package com.epm.task.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Architecture fitness functions for task-service hexagonal structure.
 *
 * <p>Enforces the four invariants of hexagonal architecture, plus two
 * framework-isolation rules: domain and application layers must not depend on
 * Jackson ({@code com.fasterxml.jackson..}) or Kafka ({@code org.apache.kafka..}).
 */
@AnalyzeClasses(
        packages = "com.epm.task",
        importOptions = {
            ImportOption.DoNotIncludeTests.class,
            ImportOption.DoNotIncludeJars.class
        })
class ArchitectureTest {

    /**
     * Rule 1 — Domain must not depend on Spring.
     */
    @ArchTest
    static final ArchRule domainMustNotDependOnSpring = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.springframework..");

    /**
     * Rule 2 — Domain must not depend on JPA.
     */
    @ArchTest
    static final ArchRule domainMustNotDependOnJpa = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("jakarta.persistence..");

    /**
     * Rule 3 — Application must not depend on Infrastructure.
     */
    @ArchTest
    static final ArchRule applicationMustNotDependOnInfrastructure = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..infrastructure..");

    /**
     * Rule 4 — Layered architecture: strict dependency direction.
     */
    @ArchTest
    static final ArchRule layerDependenciesAreRespected = layeredArchitecture()
            .consideringAllDependencies()
            .layer("Infrastructure").definedBy("..infrastructure..")
            .layer("Application").definedBy("..application..")
            .layer("Domain").definedBy("..domain..")
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure");

    /**
     * Rule 5 — Domain must not depend on Jackson.
     *
     * <p>Jackson is a serialisation framework — it belongs in the infrastructure adapter layer.
     * Domain objects must remain plain Java (serialization-agnostic).
     */
    @ArchTest
    static final ArchRule domainMustNotDependOnJackson = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.fasterxml.jackson..");

    /**
     * Rule 6 — Application must not depend on Jackson.
     *
     * <p>Use case implementations are pure Java. Serialization concerns belong in infra.
     */
    @ArchTest
    static final ArchRule applicationMustNotDependOnJackson = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.fasterxml.jackson..");

    /**
     * Rule 7 — Domain must not depend on Kafka.
     *
     * <p>Kafka is a messaging infrastructure concern. Domain events are plain records;
     * only infrastructure adapters touch the Kafka API.
     */
    @ArchTest
    static final ArchRule domainMustNotDependOnKafka = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.apache.kafka..");

    /**
     * Rule 8 — Application must not depend on Kafka.
     *
     * <p>Use case implementations must remain messaging-agnostic.
     */
    @ArchTest
    static final ArchRule applicationMustNotDependOnKafka = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.apache.kafka..");
}
