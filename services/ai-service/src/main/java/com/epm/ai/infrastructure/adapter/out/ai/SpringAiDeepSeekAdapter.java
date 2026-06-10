package com.epm.ai.infrastructure.adapter.out.ai;

import java.util.Iterator;

import com.epm.ai.domain.model.AiRequest;
import com.epm.ai.domain.model.AiResponse;
import com.epm.ai.domain.port.out.AiModelPort;
import com.epm.ai.domain.port.out.AiTokenTracker;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

/**
 * Infrastructure adapter: calls DeepSeek AI via Spring AI ChatClient.
 *
 * <p>Implements {@link AiModelPort}. Uses the auto-configured {@link ChatClient.Builder}
 * (provided by spring-ai-starter-model-openai pointed at DeepSeek).
 */
@Component
public class SpringAiDeepSeekAdapter implements AiModelPort {

    private static final Logger log = LoggerFactory.getLogger(SpringAiDeepSeekAdapter.class);
    private static final String MODEL_NAME = "deepseek-chat";
    private static final String FALLBACK_CONTENT = "AI service is currently unavailable. Please try again later.";

    private final ChatClient chatClient;
    private final AiTokenTracker tokenTracker;
    private final MeterRegistry meterRegistry;

    public SpringAiDeepSeekAdapter(ChatClient.Builder chatClientBuilder,
                                   AiTokenTracker tokenTracker,
                                   MeterRegistry meterRegistry) {
        this.chatClient = chatClientBuilder.build();
        this.tokenTracker = tokenTracker;
        this.meterRegistry = meterRegistry;
    }

    private static final String SYSTEM_PROMPT =
            "You are a JSON-only API. Respond exclusively with valid JSON. "
            + "Never add markdown, code fences, or text outside the JSON. "
            + "Output raw JSON only.";

    @Override
    public AiResponse generate(AiRequest request) {
        try {
            ChatResponse chatResponse = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(request.prompt())
                    .call()
                    .chatResponse();

            return extractResponse(chatResponse, request);
        } catch (Exception ex) {
            log.warn("DeepSeek generate failed, returning fallback: {}", ex.getMessage());
            return generateFallback(request, ex);
        }
    }

    @Override
    public AiResponse chat(AiRequest request) {
        try {
            ChatResponse chatResponse = chatClient.prompt()
                    .user(request.prompt())
                    .call()
                    .chatResponse();

            return extractResponse(chatResponse, request);
        } catch (Exception ex) {
            log.warn("DeepSeek chat failed, returning fallback: {}", ex.getMessage());
            return chatFallback(request, ex);
        }
    }

    @Override
    public Iterator<String> chatStream(AiRequest request) {
        try {
            return chatClient.prompt()
                    .user(request.prompt())
                    .stream()
                    .content()
                    .toIterable()
                    .iterator();
        } catch (Exception ex) {
            log.warn("DeepSeek chatStream failed, returning fallback: {}", ex.getMessage());
            return java.util.List.of(FALLBACK_CONTENT).iterator();
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private AiResponse extractResponse(ChatResponse chatResponse, AiRequest request) {
        String content = chatResponse.getResult().getOutput().getText();
        log.info("DeepSeek RAW response (first 500 chars): [{}]",
                content != null && content.length() > 500 ? content.substring(0, 500) : content);
        Usage usage = chatResponse.getMetadata().getUsage();
        int inputTokens = (usage != null && usage.getPromptTokens() != null) ? usage.getPromptTokens() : 0;
        int outputTokens = (usage != null && usage.getCompletionTokens() != null) ? usage.getCompletionTokens() : 0;

        tokenTracker.trackTokens(inputTokens, outputTokens, MODEL_NAME, estimateCost(inputTokens, outputTokens));

        meterRegistry.counter("ai.tokens.used", "model", MODEL_NAME, "type", "input").increment(inputTokens);
        meterRegistry.counter("ai.tokens.used", "model", MODEL_NAME, "type", "output").increment(outputTokens);

        return new AiResponse(content, inputTokens, outputTokens, MODEL_NAME, false);
    }

    private AiResponse generateFallback(AiRequest request, Throwable t) {
        log.error("Circuit breaker / exception fallback triggered for generate: {}", t.getMessage());
        return new AiResponse(FALLBACK_CONTENT, 0, 0, MODEL_NAME, false);
    }

    private AiResponse chatFallback(AiRequest request, Throwable t) {
        log.error("Circuit breaker / exception fallback triggered for chat: {}", t.getMessage());
        return new AiResponse(FALLBACK_CONTENT, 0, 0, MODEL_NAME, false);
    }

    private double estimateCost(int inputTokens, int outputTokens) {
        return (inputTokens / 1000.0) * 0.00014 + (outputTokens / 1000.0) * 0.00028;
    }
}
