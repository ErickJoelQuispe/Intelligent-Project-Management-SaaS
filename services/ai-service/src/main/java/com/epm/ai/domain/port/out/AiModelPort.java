package com.epm.ai.domain.port.out;

import java.util.Iterator;

import com.epm.ai.domain.model.AiRequest;
import com.epm.ai.domain.model.AiResponse;

/**
 * Driven port: abstraction over the AI model adapter (e.g., DeepSeek via Spring AI).
 */
public interface AiModelPort {

    /**
     * Sends a generation request to the AI model and returns the full response.
     */
    AiResponse generate(AiRequest request);

    /**
     * Sends a chat request to the AI model and returns the full response.
     */
    AiResponse chat(AiRequest request);

    /**
     * Sends a chat request to the AI model and returns a streaming response.
     *
     * <p>Each {@code Iterator} element is a chunk of text from the model.
     * The iterator is exhausted when the model finishes generating.
     */
    Iterator<String> chatStream(AiRequest request);
}
