package com.epm.ai.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.epm.ai.domain.event.AiTasksGenerated;
import com.epm.ai.domain.model.AiRequest;
import com.epm.ai.domain.model.AiResponse;
import com.epm.ai.domain.model.ProjectContext;
import com.epm.ai.domain.model.TaskDraft;
import com.epm.ai.domain.model.TaskPriority;
import com.epm.ai.domain.port.out.AiCachePort;
import com.epm.ai.domain.port.out.AiEventPublisher;
import com.epm.ai.domain.port.out.AiModelPort;
import com.epm.ai.domain.port.out.AiTokenTracker;
import com.epm.ai.domain.port.out.ProjectContextPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for GenerateTasksUseCaseImpl.
 */
@ExtendWith(MockitoExtension.class)
class GenerateTasksUseCaseImplTest {

    @Mock AiModelPort modelPort;
    @Mock ProjectContextPort contextPort;
    @Mock AiEventPublisher eventPublisher;
    @Mock AiCachePort cachePort;
    @Mock AiTokenTracker tokenTracker;

    GenerateTasksUseCaseImpl useCase;

    private static final ProjectContext SAMPLE_CONTEXT = new ProjectContext(
            "proj-1", "My Project", "A SaaS app", List.of("Alice", "Bob"));

    private static final String TASKS_JSON = """
            [
              {"title":"Set up CI/CD","description":"Configure GitHub Actions","priority":"HIGH"},
              {"title":"Write tests","description":"Add unit tests","priority":"MEDIUM"}
            ]
            """;

    private static final AiResponse MODEL_RESPONSE =
            new AiResponse(TASKS_JSON, 100, 200, "deepseek-chat", false);

    @BeforeEach
    void setUp() {
        useCase = new GenerateTasksUseCaseImpl(modelPort, contextPort, eventPublisher, cachePort, tokenTracker);
    }

    // --- Cache HIT: model is never called ---

    @Test
    void execute_cacheHit_returnsCachedTasksWithoutCallingModel() {
        AiResponse cached = new AiResponse(TASKS_JSON, 100, 200, "deepseek-chat", true);
        when(cachePort.get(anyString())).thenReturn(Optional.of(cached));
        when(contextPort.fetchProjectContext("proj-1", "tenant-1")).thenReturn(SAMPLE_CONTEXT);

        List<TaskDraft> tasks = useCase.execute("proj-1", "user-1", "tenant-1", "Build login", false, List.of());

        assertThat(tasks).hasSize(2);
        assertThat(tasks.get(0).title()).isEqualTo("Set up CI/CD");
        assertThat(tasks.get(0).priority()).isEqualTo(TaskPriority.HIGH);
        verify(modelPort, never()).generate(any());
        verify(eventPublisher, never()).publish(any());
    }

    // --- Cache MISS: model called, event published, tokens tracked ---

    @Test
    void execute_cacheMiss_callsModelCachesAndPublishesEvent() {
        when(cachePort.get(anyString())).thenReturn(Optional.empty());
        when(contextPort.fetchProjectContext("proj-1", "tenant-1")).thenReturn(SAMPLE_CONTEXT);
        when(modelPort.generate(any(AiRequest.class))).thenReturn(MODEL_RESPONSE);

        List<TaskDraft> tasks = useCase.execute("proj-1", "user-1", "tenant-1", "Build login", false, List.of());

        assertThat(tasks).hasSize(2);
        assertThat(tasks.get(1).title()).isEqualTo("Write tests");
        assertThat(tasks.get(1).priority()).isEqualTo(TaskPriority.MEDIUM);
        verify(modelPort).generate(any(AiRequest.class));
        verify(cachePort).put(anyString(), eq(MODEL_RESPONSE), anyLong());
        verify(tokenTracker).trackTokens(eq(100), eq(200), eq("deepseek-chat"), any(Double.class));
    }

    @Test
    void execute_cacheMiss_publishesAiTasksGeneratedEvent() {
        when(cachePort.get(anyString())).thenReturn(Optional.empty());
        when(contextPort.fetchProjectContext("proj-1", "tenant-1")).thenReturn(SAMPLE_CONTEXT);
        when(modelPort.generate(any(AiRequest.class))).thenReturn(MODEL_RESPONSE);

        useCase.execute("proj-1", "user-1", "tenant-1", "Build login", false, List.of());

        ArgumentCaptor<AiTasksGenerated> captor = ArgumentCaptor.forClass(AiTasksGenerated.class);
        verify(eventPublisher).publish(captor.capture());
        AiTasksGenerated event = captor.getValue();
        assertThat(event.projectId()).isEqualTo("proj-1");
        assertThat(event.tenantId()).isEqualTo("tenant-1");
        assertThat(event.generatedBy()).isEqualTo("user-1");
        assertThat(event.tasks()).hasSize(2);
        assertThat(event.eventId()).isNotNull();
    }

    // --- Bypass cache: always calls model ---

    @Test
    void execute_bypassCache_alwaysCallsModelEvenWhenCacheHasData() {
        when(contextPort.fetchProjectContext("proj-1", "tenant-1")).thenReturn(SAMPLE_CONTEXT);
        when(modelPort.generate(any(AiRequest.class))).thenReturn(MODEL_RESPONSE);

        List<TaskDraft> tasks = useCase.execute("proj-1", "user-1", "tenant-1", "Build login", true, List.of());

        assertThat(tasks).hasSize(2);
        verify(modelPort).generate(any(AiRequest.class));
        verify(cachePort, never()).get(anyString());
    }

    // --- Model failure ---

    @Test
    void execute_modelThrowsException_propagatesWithClearMessage() {
        when(cachePort.get(anyString())).thenReturn(Optional.empty());
        when(contextPort.fetchProjectContext("proj-1", "tenant-1")).thenReturn(SAMPLE_CONTEXT);
        when(modelPort.generate(any(AiRequest.class)))
                .thenThrow(new RuntimeException("DeepSeek connection refused"));

        assertThatThrownBy(() ->
                useCase.execute("proj-1", "user-1", "tenant-1", "Build login", false, List.of()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DeepSeek connection refused");
    }
}
