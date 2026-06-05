package com.epm.ai.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.epm.ai.domain.exception.ProjectNotFoundException;
import com.epm.ai.domain.model.AiRequest;
import com.epm.ai.domain.model.AiResponse;
import com.epm.ai.domain.model.ProjectContext;
import com.epm.ai.domain.port.out.AiCachePort;
import com.epm.ai.domain.port.out.AiModelPort;
import com.epm.ai.domain.port.out.AiTokenTracker;
import com.epm.ai.domain.port.out.ProjectContextPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for SummarizeProjectUseCaseImpl.
 */
@ExtendWith(MockitoExtension.class)
class SummarizeProjectUseCaseImplTest {

    @Mock AiModelPort modelPort;
    @Mock ProjectContextPort contextPort;
    @Mock AiCachePort cachePort;
    @Mock AiTokenTracker tokenTracker;

    SummarizeProjectUseCaseImpl useCase;

    private static final ProjectContext SAMPLE_CONTEXT = new ProjectContext(
            "proj-1", "SaaS App", "A project management tool", List.of("Alice", "Bob"));

    private static final String SUMMARY_TEXT = "SaaS App is progressing well with 2 members.";
    private static final AiResponse MODEL_RESPONSE =
            new AiResponse(SUMMARY_TEXT, 80, 150, "deepseek-chat", false);

    @BeforeEach
    void setUp() {
        useCase = new SummarizeProjectUseCaseImpl(modelPort, contextPort, cachePort, tokenTracker);
    }

    // --- Cache MISS: model is called and result cached ---

    @Test
    void execute_cacheMiss_callsModelAndReturnsSummary() {
        when(contextPort.fetchProjectContext("proj-1", "tenant-1")).thenReturn(SAMPLE_CONTEXT);
        when(cachePort.get(anyString())).thenReturn(Optional.empty());
        when(modelPort.generate(any(AiRequest.class))).thenReturn(MODEL_RESPONSE);

        String summary = useCase.execute("proj-1", "user-1", "tenant-1");

        assertThat(summary).isEqualTo(SUMMARY_TEXT);
        verify(modelPort).generate(any(AiRequest.class));
        verify(cachePort).put(anyString(), any(AiResponse.class), anyLong());
        verify(tokenTracker).trackTokens(80, 150, "deepseek-chat", any(Double.class));
    }

    // --- Cache HIT: model is NOT called ---

    @Test
    void execute_cacheHit_returnsCachedSummaryWithoutCallingModel() {
        AiResponse cached = new AiResponse(SUMMARY_TEXT, 80, 150, "deepseek-chat", true);
        when(contextPort.fetchProjectContext("proj-1", "tenant-1")).thenReturn(SAMPLE_CONTEXT);
        when(cachePort.get(anyString())).thenReturn(Optional.of(cached));

        String summary = useCase.execute("proj-1", "user-1", "tenant-1");

        assertThat(summary).isEqualTo(SUMMARY_TEXT);
        verify(modelPort, never()).generate(any());
    }

    // --- Project not found: propagated as exception ---

    @Test
    void execute_projectNotFound_propagatesException() {
        when(contextPort.fetchProjectContext("missing", "tenant-1"))
                .thenThrow(new ProjectNotFoundException("missing"));

        assertThatThrownBy(() -> useCase.execute("missing", "user-1", "tenant-1"))
                .isInstanceOf(ProjectNotFoundException.class)
                .hasMessageContaining("missing");
    }

    // --- Model failure: graceful error message ---

    @Test
    void execute_modelFailure_propagatesWithGracefulMessage() {
        when(contextPort.fetchProjectContext("proj-1", "tenant-1")).thenReturn(SAMPLE_CONTEXT);
        when(cachePort.get(anyString())).thenReturn(Optional.empty());
        when(modelPort.generate(any(AiRequest.class)))
                .thenThrow(new RuntimeException("AI service temporarily unavailable"));

        assertThatThrownBy(() -> useCase.execute("proj-1", "user-1", "tenant-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("AI service temporarily unavailable");
    }
}
