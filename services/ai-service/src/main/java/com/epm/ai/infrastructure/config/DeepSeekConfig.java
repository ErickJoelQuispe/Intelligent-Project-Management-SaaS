package com.epm.ai.infrastructure.config;

import org.springframework.context.annotation.Configuration;

/**
 * DeepSeek AI model configuration for ai-service.
 *
 * <p>Spring AI's {@code spring-ai-starter-model-openai} auto-configures:
 * <ul>
 *   <li>{@code ChatModel} from {@code spring.ai.openai.*} properties</li>
 *   <li>{@code ChatClient.Builder} from the auto-configured {@code ChatModel}</li>
 * </ul>
 *
 * <p>No explicit bean declarations are needed here — the starter handles everything.
 * The {@code spring.ai.openai.base-url} property points to DeepSeek's API endpoint.
 */
@Configuration
public class DeepSeekConfig {
    // Auto-configured by spring-ai-starter-model-openai:
    //   ChatModel (OpenAI-compatible, pointed at DeepSeek)
    //   ChatClient.Builder (injectable in SpringAiDeepSeekAdapter)
}
