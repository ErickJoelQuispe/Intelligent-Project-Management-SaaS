package com.epm.ai.infrastructure.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.epm.ai.domain.model.AiRequest;
import com.epm.ai.domain.model.AiResponse;
import com.epm.ai.domain.port.out.AiTokenTracker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
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
    private CircuitBreakerRegistry circuitBreakerRegistry;
    private CircuitBreaker circuitBreaker;
    private SpringAiDeepSeekAdapter adapter;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        // Sensitive CB config so tests can open/recover the breaker fast and deterministically.
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .waitDurationInOpenState(java.time.Duration.ofMillis(100))
                .permittedNumberOfCallsInHalfOpenState(1)
                .build();
        circuitBreakerRegistry = CircuitBreakerRegistry.of(config);
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("deepseek");
        when(chatClientBuilder.build()).thenReturn(chatClient);
        adapter = new SpringAiDeepSeekAdapter(
                chatClientBuilder, tokenTracker, meterRegistry, circuitBreakerRegistry);
    }

    // ── generate: returns valid AiResponse ───────────────────────────────────

    @Test
    void generate_returnsValidAiResponse() {
        // Arrange: mock the ChatClient fluent chain
        // generate() calls .prompt().system(...).user(...).call() — system() must be mocked first.
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.ChatClientRequestSpec afterSystemSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        ChatResponse chatResponse = buildChatResponse("Task list generated", 100, 50);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(afterSystemSpec);
        when(afterSystemSpec.user(anyString())).thenReturn(afterSystemSpec);
        when(afterSystemSpec.call()).thenReturn(callSpec);
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
        // generate() calls .prompt().system(...).user(...).call() — system() must be mocked.
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.ChatClientRequestSpec afterSystemSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        ChatResponse chatResponse = buildChatResponse("Summary text", 200, 80);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(afterSystemSpec);
        when(afterSystemSpec.user(anyString())).thenReturn(afterSystemSpec);
        when(afterSystemSpec.call()).thenReturn(callSpec);
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
        // chat() calls .prompt().system(...).user(...).call() — same chain as generate()
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.ChatClientRequestSpec afterSystemSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        ChatResponse chatResponse = buildChatResponse("The project is on track.", 60, 30);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(afterSystemSpec);
        when(afterSystemSpec.user(anyString())).thenReturn(afterSystemSpec);
        when(afterSystemSpec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenReturn(chatResponse);

        AiRequest request = new AiRequest("How is the project?", "proj-1", "user-1", "tenant-1");
        AiResponse response = adapter.chat(request);

        assertThat(response.content()).isEqualTo("The project is on track.");
        assertThat(response.inputTokens()).isEqualTo(60);
    }

    // ── circuit breaker: records failures and opens ──────────────────────────

    @Test
    void circuitBreaker_opens_afterFailuresExceedThreshold() {
        // DeepSeek throws on every call. With slidingWindowSize=2 and failureRate=50%,
        // two failures must transition the CB to OPEN.
        when(chatClient.prompt()).thenThrow(new RuntimeException("Connection refused"));
        AiRequest request = new AiRequest("Generate tasks", "proj-1", "user-1", "tenant-1");

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        adapter.generate(request); // failure 1 (fallback returned, CB records failure)
        adapter.generate(request); // failure 2 → CB opens

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    // ── circuit breaker: open returns fallback without calling DeepSeek ───────

    @Test
    void circuitBreaker_open_returnsFallback_withoutCallingDeepSeek() {
        circuitBreaker.transitionToOpenState();
        AiRequest request = new AiRequest("Generate tasks", "proj-1", "user-1", "tenant-1");

        AiResponse response = adapter.generate(request);

        assertThat(response).isNotNull();
        assertThat(response.content()).contains("unavailable");
        verify(chatClient, never()).prompt();
    }

    // ── circuit breaker: recovers to CLOSED on success in HALF_OPEN ───────────

    @Test
    void circuitBreaker_recoversToClosed_afterSuccessfulCallInHalfOpen() {
        // Open the breaker, then move to HALF_OPEN deterministically (no sleep).
        circuitBreaker.transitionToOpenState();
        circuitBreaker.transitionToHalfOpenState();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        // Mock a successful DeepSeek call.
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.ChatClientRequestSpec afterSystemSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        ChatResponse chatResponse = buildChatResponse("Recovered", 10, 5);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(afterSystemSpec);
        when(afterSystemSpec.user(anyString())).thenReturn(afterSystemSpec);
        when(afterSystemSpec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenReturn(chatResponse);

        AiRequest request = new AiRequest("Generate tasks", "proj-1", "user-1", "tenant-1");
        AiResponse response = adapter.generate(request);

        assertThat(response.content()).isEqualTo("Recovered");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
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
