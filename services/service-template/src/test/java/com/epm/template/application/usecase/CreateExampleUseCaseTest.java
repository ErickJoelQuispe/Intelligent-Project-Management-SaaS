package com.epm.template.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.epm.template.domain.model.Example;
import com.epm.template.domain.port.out.TransactionalExampleWriter;
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
 *
 * <p>The use case must delegate to {@link TransactionalExampleWriter#saveAndPublish},
 * NOT to separate repository + publisher calls. Atomicity is enforced by the writer.
 */
@ExtendWith(MockitoExtension.class)
class CreateExampleUseCaseTest {

    @Mock
    private TransactionalExampleWriter writer;

    private CreateExampleUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateExampleUseCaseImpl(writer);
    }

    @Test
    void createDelegatesToTransactionalWriterAndReturnsAggregate() {
        UUID tenantId = UUID.randomUUID();
        Example savedExample = Example.reconstitute(UUID.randomUUID(), tenantId, "Test example");
        when(writer.saveAndPublish(any(Example.class))).thenReturn(savedExample);

        Example result = useCase.create(tenantId, "Test example");

        assertThat(result.name()).isEqualTo("Test example");
        assertThat(result.tenantId()).isEqualTo(tenantId);
        verify(writer).saveAndPublish(any(Example.class));
    }

    @Test
    void createPassesTenantIdAndNameToAggregate() {
        UUID tenantId = UUID.randomUUID();
        Example savedExample = Example.reconstitute(UUID.randomUUID(), tenantId, "Named example");
        when(writer.saveAndPublish(any(Example.class))).thenReturn(savedExample);

        Example result = useCase.create(tenantId, "Named example");

        assertThat(result.tenantId()).isEqualTo(tenantId);
        assertThat(result.name()).isEqualTo("Named example");
    }
}
