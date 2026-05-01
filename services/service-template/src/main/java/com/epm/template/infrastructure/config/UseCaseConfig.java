package com.epm.template.infrastructure.config;

import com.epm.template.application.usecase.CreateExampleUseCaseImpl;
import com.epm.template.application.usecase.FindExampleUseCaseImpl;
import com.epm.template.domain.port.in.CreateExampleUseCase;
import com.epm.template.domain.port.in.FindExampleUseCase;
import com.epm.template.domain.port.out.ExampleEventPublisher;
import com.epm.template.domain.port.out.ExampleRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires use case implementations with their ports.
 *
 * <p>Use case classes have no Spring annotations — they are pure Java.
 * This @Configuration class is the only place that knows about Spring
 * and performs the dependency injection manually. This keeps the
 * application layer framework-free and easily testable.
 */
@Configuration
class UseCaseConfig {

    @Bean
    CreateExampleUseCase createExampleUseCase(
            ExampleRepository repository, ExampleEventPublisher eventPublisher) {
        return new CreateExampleUseCaseImpl(repository, eventPublisher);
    }

    @Bean
    FindExampleUseCase findExampleUseCase(ExampleRepository repository) {
        return new FindExampleUseCaseImpl(repository);
    }
}
