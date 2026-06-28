package com.epm.ai.application.usecase;

import java.util.Optional;

import com.epm.ai.domain.model.AiRequest;
import com.epm.ai.domain.model.AiResponse;
import com.epm.ai.domain.model.ProjectContext;
import com.epm.ai.domain.port.in.SummarizeProjectUseCase;
import com.epm.ai.domain.port.out.AiCachePort;
import com.epm.ai.domain.port.out.AiModelPort;
import com.epm.ai.domain.port.out.AiTokenTracker;
import com.epm.ai.domain.port.out.ProjectContextPort;
import com.epm.ai.domain.service.CacheKeyGenerator;

/**
 * Application use case: generate a concise AI summary for a project.
 *
 * <p>Flow:
 * <ol>
 *   <li>Fetch project context via {@link ProjectContextPort}</li>
 *   <li>Check cache (key = SHA-256 of "project-summary:{projectId}:{name}")</li>
 *   <li>On cache miss: call {@link AiModelPort#generate}, cache the response, track tokens</li>
 *   <li>Return summary string</li>
 * </ol>
 *
 * <p>Pure Java — no Spring, no infrastructure annotations.
 */
public class SummarizeProjectUseCaseImpl implements SummarizeProjectUseCase {

    private static final long CACHE_TTL_SECONDS = 3600L;
    private static final double COST_PER_1K_INPUT = 0.00014;
    private static final double COST_PER_1K_OUTPUT = 0.00028;

    private final AiModelPort modelPort;
    private final ProjectContextPort contextPort;
    private final AiCachePort cachePort;
    private final AiTokenTracker tokenTracker;

    public SummarizeProjectUseCaseImpl(AiModelPort modelPort,
                                       ProjectContextPort contextPort,
                                       AiCachePort cachePort,
                                       AiTokenTracker tokenTracker) {
        this.modelPort = modelPort;
        this.contextPort = contextPort;
        this.cachePort = cachePort;
        this.tokenTracker = tokenTracker;
    }

    @Override
    public String execute(String projectId, String userId, String tenantId) {
        ProjectContext context = contextPort.fetchProjectContext(projectId, tenantId);
        String cacheKey = CacheKeyGenerator.generate("project-summary", projectId, context.name());

        Optional<AiResponse> cached = cachePort.get(cacheKey);
        if (cached.isPresent()) {
            return cached.get().content();
        }

        String prompt = buildPrompt(context);
        AiRequest request = new AiRequest(prompt, projectId, userId, tenantId);
        AiResponse response = modelPort.generate(request);

        cachePort.put(cacheKey, response, CACHE_TTL_SECONDS);
        trackCost(response);

        return response.content();
    }

    private String buildPrompt(ProjectContext context) {
        String members = context.memberNames().isEmpty()
                ? "No members assigned yet"
                : String.join(", ", context.memberNames());
        return "Analyze this project and respond with ONLY a JSON object.\n"
                + "Do NOT use a 'summary' key. Use EXACTLY these three keys: status, risks, milestones.\n\n"
                + "Project name: " + context.name() + "\n"
                + "Description: " + context.description() + "\n"
                + "Members: " + members + "\n\n"
                + "Required JSON format (no other text, no markdown):\n"
                + "{\"status\":\"<one sentence about current state>\","
                + "\"risks\":[\"<risk 1>\",\"<risk 2>\",\"<risk 3>\"],"
                + "\"milestones\":[\"<next step 1>\",\"<next step 2>\",\"<next step 3>\"]}\n\n"
                + "Rules: each risk and milestone under 12 words. status under 20 words.";
    }

    private void trackCost(AiResponse response) {
        double cost = (response.inputTokens() / 1000.0) * COST_PER_1K_INPUT
                + (response.outputTokens() / 1000.0) * COST_PER_1K_OUTPUT;
        tokenTracker.trackTokens(response.inputTokens(), response.outputTokens(), response.model(), cost);
    }
}
