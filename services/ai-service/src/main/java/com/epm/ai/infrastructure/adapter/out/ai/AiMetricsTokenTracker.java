package com.epm.ai.infrastructure.adapter.out.ai;

import com.epm.ai.domain.port.out.AiTokenTracker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Implements {@link AiTokenTracker} using Micrometer metrics.
 *
 * <p>Tracks:
 * <ul>
 *   <li>{@code ai.tokens.used} (tags: model, type=input|output)</li>
 *   <li>{@code ai.cost.total} (tag: model)</li>
 * </ul>
 */
@Component
public class AiMetricsTokenTracker implements AiTokenTracker {

    private final MeterRegistry meterRegistry;

    public AiMetricsTokenTracker(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void trackTokens(int inputTokens, int outputTokens, String model, double estimatedCost) {
        Counter.builder("ai.tokens.used")
                .tag("model", model)
                .tag("type", "input")
                .register(meterRegistry)
                .increment(inputTokens);

        Counter.builder("ai.tokens.used")
                .tag("model", model)
                .tag("type", "output")
                .register(meterRegistry)
                .increment(outputTokens);

        Counter.builder("ai.cost.total")
                .tag("model", model)
                .register(meterRegistry)
                .increment(estimatedCost);
    }
}
