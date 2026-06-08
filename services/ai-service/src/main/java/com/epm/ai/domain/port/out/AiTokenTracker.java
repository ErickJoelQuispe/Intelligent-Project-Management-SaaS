package com.epm.ai.domain.port.out;

/**
 * Driven port: track AI token usage and cost metrics.
 */
public interface AiTokenTracker {

    /**
     * Records token consumption and estimated cost for a completed AI call.
     *
     * @param inputTokens    number of tokens in the prompt
     * @param outputTokens   number of tokens in the completion
     * @param model          the AI model that was used
     * @param estimatedCost  estimated monetary cost for this call
     */
    void trackTokens(int inputTokens, int outputTokens, String model, double estimatedCost);
}
