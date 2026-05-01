package com.epm.template.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.epm.template.domain.event.ExampleCreatedEvent;
import com.epm.template.domain.model.Example;
import com.epm.template.domain.port.out.ExampleEventPublisher;
import com.epm.template.domain.port.out.ExampleRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link CreateExampleUseCaseImpl}.
 *
 * <p>Uses Mockito to stub driven ports. No Spring context needed —
 * the use case is plain Java and instantiated directly in the test.
 */
@ExtendWith(MockitoExtension.class)
class CreateExampleUseCaseTest {

    @Mock
    private ExampleRepository repository;

    @Mock
    private ExampleEventPublisher eventPublisher;

    private CreateExampleUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateExampleUseCaseImpl(repository, eventPublisher);
    }

    @Test
    void shouldSaveAndPublishEventOnCreate() {
        Example savedExample = new Example(UUID.randomUUID(), "Test example");
        when(repository.save(any(Example.class))).thenReturn(savedExample);

        Example result = useCase.create("Test example");

        assertThat(result.name()).isEqualTo("Test example");
        verify(repository).save(any(Example.class));
        verify(eventPublisher).publish(any(ExampleCreatedEvent.class));
    }
}
