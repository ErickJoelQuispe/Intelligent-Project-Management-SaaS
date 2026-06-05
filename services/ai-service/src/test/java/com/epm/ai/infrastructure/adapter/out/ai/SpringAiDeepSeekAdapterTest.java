package com.epm.ai.infrastructure.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.epm.ai.domain.model.AiRequest;
import com.epm.ai.domain.model.AiResponse;
import com.epm.ai.domain.port.out.AiTokenTracker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * Unit tests for {@link SpringAiDeepSeekAdapter}.
 * Uses mocked ChatClient — no real DeepSeek API needed.
 */
@ExtendWith(MockitoExtension.class)
class SpringAiDeepSeekAdapterTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private AiTokenTracker tokenTracker;

    private MeterRegistry meterRegistry;
    private SpringAiDeepSeekAdapter adapter;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        when(chatClientBuilder.build()).thenReturn(chatClient);
        adapter = new SpringAiDeepSeekAdapter(chatClientBuilder, tokenTracker, meterRegistry);
    }

    // ── generate: returns valid AiResponse ───────────────────────────────────

    @Test
    void generate_returnsValidAiResponse() {
        // Arrange: mock the ChatClient fluent chain
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.ChatClientRequestSpec sysSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        ChatResponse chatResponse = buildChatResponse("Task list generated", 100, 50);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(sysSpec);
        when(sysSpec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenReturn(chatResponse);

        AiRequest request = new AiRequest("Generate tasks for auth module", "proj-1", "user-1", "tenant-1");

        // Act
        AiResponse response = adapter.generate(request);

        // Assert: real content from mocked response
        assertThat(response.content()).isEqualTo("Task list generated");
        assertThat(response.inputTokens()).isEqualTo(100);
        assertThat(response.outputTokens()).isEqualTo(50);
        assertThat(response.cached()).isFalse();
    }

    // ── generate: token tracker is called ────────────────────────────────────

    @Test
    void generate_callsTokenTracker() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.ChatClientRequestSpec sysSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        ChatResponse chatResponse = buildChatResponse("Summary text", 200, 80);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(sysSpec);
        when(sysSpec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenReturn(chatResponse);

        AiRequest request = new AiRequest("Summarize project", "proj-1", "user-1", "tenant-1");
        adapter.generate(request);

        // Token tracker must be called with actual token counts
        verify(tokenTracker).trackTokens(
                org.mockito.ArgumentMatchers.eq(200),
                org.mockito.ArgumentMatchers.eq(80),
                org.mockito.ArgumentMatchers.eq("deepseek-chat"),
                org.mockito.ArgumentMatchers.anyDouble());
    }

    // ── generate: fallback on exception ──────────────────────────────────────

    @Test
    void generate_returnsFallbackResponse_onException() {
        when(chatClient.prompt()).thenThrow(new RuntimeException("Connection refused"));

        AiRequest request = new AiRequest("Generate tasks", "proj-1", "user-1", "tenant-1");
        AiResponse response = adapter.generate(request);

        // Fallback must return graceful error response (not throw)
        assertThat(response).isNotNull();
        assertThat(response.content()).contains("unavailable");
    }

    // ── chat: returns valid AiResponse ───────────────────────────────────────

    @Test
    void chat_returnsValidAiResponse() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.ChatClientRequestSpec sysSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        ChatResponse chatResponse = buildChatResponse("The project is on track.", 60, 30);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(sysSpec);
        when(sysSpec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenReturn(chatResponse);

        AiRequest request = new AiRequest("How is the project?", "proj-1", "user-1", "tenant-1");
        AiResponse response = adapter.chat(request);

        assertThat(response.content()).isEqualTo("The project is on track.");
        assertThat(response.inputTokens()).isEqualTo(60);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String anyString() {
        return any(String.class);
    }

    private ChatResponse buildChatResponse(String content, int inputTokens, int outputTokens) {
        ChatResponse chatResponse = mock(ChatResponse.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        Usage usage = mock(Usage.class);
        org.springframework.ai.chat.model.Generation generation = mock(org.springframework.ai.chat.model.Generation.class);
        org.springframework.ai.chat.messages.AssistantMessage assistantMessage = mock(org.springframework.ai.chat.messages.AssistantMessage.class);

        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(assistantMessage.getText()).thenReturn(content);
        when(chatResponse.getMetadata()).thenReturn(metadata);
        when(metadata.getUsage()).thenReturn(usage);
        when(usage.getPromptTokens()).thenReturn(inputTokens);
        when(usage.getCompletionTokens()).thenReturn(outputTokens);

        return chatResponse;
    }
}
