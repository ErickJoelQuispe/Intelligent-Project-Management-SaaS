package com.epm.ai.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.epm.ai.domain.exception.ProjectNotFoundException;
import com.epm.ai.domain.model.AiRequest;
import com.epm.ai.domain.model.AiResponse;
import com.epm.ai.domain.model.ProjectContext;
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
 * Unit tests for ChatWithProjectUseCaseImpl.
 */
@ExtendWith(MockitoExtension.class)
class ChatWithProjectUseCaseImplTest {

    @Mock AiModelPort modelPort;
    @Mock ProjectContextPort contextPort;
    @Mock AiTokenTracker tokenTracker;

    ChatWithProjectUseCaseImpl useCase;

    private static final ProjectContext SAMPLE_CONTEXT = new ProjectContext(
            "proj-1", "My App", "Project management SaaS", List.of("Alice", "Bob"));

    @BeforeEach
    void setUp() {
        useCase = new ChatWithProjectUseCaseImpl(modelPort, contextPort, tokenTracker);
    }

    // --- Happy path: context fetched, model called, response returned ---

    @Test
    void execute_fetchesContextAndCallsChatModel() {
        AiResponse modelResponse = new AiResponse("The project has 2 members.", 50, 80, "deepseek-chat", false);
        when(contextPort.fetchProjectContext("proj-1", "tenant-1")).thenReturn(SAMPLE_CONTEXT);
        when(modelPort.chat(any(AiRequest.class))).thenReturn(modelResponse);

        AiResponse response = useCase.execute("proj-1", "user-1", "tenant-1", "How many members?");

        assertThat(response.content()).isEqualTo("The project has 2 members.");
        assertThat(response.inputTokens()).isEqualTo(50);
        assertThat(response.outputTokens()).isEqualTo(80);
        verify(modelPort).chat(any(AiRequest.class));
        verify(tokenTracker).trackTokens(eq(50), eq(80), eq("deepseek-chat"), any(Double.class));
    }

    // --- Project context is injected into the request prompt ---

    @Test
    void execute_includesProjectContextInRequestPrompt() {
        AiResponse modelResponse = new AiResponse("Answer", 30, 60, "deepseek-chat", false);
        when(contextPort.fetchProjectContext("proj-1", "tenant-1")).thenReturn(SAMPLE_CONTEXT);
        when(modelPort.chat(any(AiRequest.class))).thenReturn(modelResponse);

        useCase.execute("proj-1", "user-1", "tenant-1", "What is the project about?");

        ArgumentCaptor<AiRequest> captor = ArgumentCaptor.forClass(AiRequest.class);
        verify(modelPort).chat(captor.capture());
        AiRequest sentRequest = captor.getValue();
        assertThat(sentRequest.prompt()).contains("My App");
        assertThat(sentRequest.prompt()).contains("Project management SaaS");
        assertThat(sentRequest.projectId()).isEqualTo("proj-1");
        assertThat(sentRequest.userId()).isEqualTo("user-1");
        assertThat(sentRequest.tenantId()).isEqualTo("tenant-1");
    }

    // --- Project not found ---

    @Test
    void execute_projectNotFound_propagatesException() {
        when(contextPort.fetchProjectContext("missing", "tenant-1"))
                .thenThrow(new ProjectNotFoundException("missing"));

        assertThatThrownBy(() -> useCase.execute("missing", "user-1", "tenant-1", "Any question?"))
                .isInstanceOf(ProjectNotFoundException.class)
                .hasMessageContaining("missing");
    }

    // --- Model failure ---

    @Test
    void execute_modelFailure_propagatesWithGracefulMessage() {
        when(contextPort.fetchProjectContext("proj-1", "tenant-1")).thenReturn(SAMPLE_CONTEXT);
        when(modelPort.chat(any(AiRequest.class)))
                .thenThrow(new RuntimeException("AI service temporarily unavailable"));

        assertThatThrownBy(() -> useCase.execute("proj-1", "user-1", "tenant-1", "Any question?"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("AI service temporarily unavailable");
    }
}
