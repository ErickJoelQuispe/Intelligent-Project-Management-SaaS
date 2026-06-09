package com.epm.user.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Architecture fitness functions for user-service hexagonal structure.
 *
 * <p>Enforces the four invariants of hexagonal architecture.
 */
@AnalyzeClasses(
        packages = "com.epm.user",
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
}
