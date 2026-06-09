package com.epm.auth.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Architecture fitness functions for auth-service hexagonal structure.
 *
 * <p>These tests run on every build and enforce the dependency rules that define
 * hexagonal architecture. If any rule is violated, the build fails — no exceptions.
 *
 * <p>The four invariants enforced here:
 * <ol>
 *   <li>Domain is Spring-free: no org.springframework imports in domain classes.
 *   <li>Domain is JPA-free: no jakarta.persistence imports in domain classes.
 *   <li>Application is infrastructure-free: use cases do not import adapters.
 *   <li>Layered dependency direction: domain ← application ← infrastructure (never reversed).
 * </ol>
 */
@AnalyzeClasses(
        packages = "com.epm.auth",
        importOptions = {
            ImportOption.DoNotIncludeTests.class,
            ImportOption.DoNotIncludeJars.class
        })
class ArchitectureTest {

    /**
     * Rule 1 — Domain must not depend on Spring.
     *
     * <p>If a domain class imports anything from {@code org.springframework},
     * the domain is no longer portable. It becomes coupled to the framework
     * and cannot be tested without starting a Spring context.
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
     *
     * <p>JPA annotations ({@code @Entity}, {@code @Column}, etc.) in domain classes
     * couple the business model to a specific persistence strategy.
     * JPA entities belong in {@code infrastructure.adapter.out.persistence}.
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
     *
     * <p>Use cases define *what* the system does; adapters define *how*.
     * If a use case imports an adapter, the system loses the ability to
     * swap implementations without modifying business logic.
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
     *
     * <p>Codifies the full dependency graph as a single declarative rule:
     * infrastructure can access application and domain,
     * application can only access domain,
     * domain depends on nothing inside the project.
     */
    @ArchTest
    static final ArchRule layerDependenciesAreRespected = layeredArchitecture()
            .consideringAllDependencies()
            .layer("Infrastructure").definedBy("..infrastructure..")
            .layer("Application").definedBy("..application..")
            .layer("Domain").definedBy("..domain..")
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure")
            // AuthServiceApplication is the Spring Boot entry point (root package) — it legitimately
            // references @ConfigurationProperties from infrastructure to wire the app context.
            .ignoreDependency(
                    com.epm.auth.AuthServiceApplication.class,
                    com.epm.auth.infrastructure.config.KeycloakProperties.class);
}
