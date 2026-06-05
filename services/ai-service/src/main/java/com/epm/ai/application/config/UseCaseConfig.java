package com.epm.ai.application.config;

import com.epm.ai.application.usecase.ChatWithProjectUseCaseImpl;
import com.epm.ai.application.usecase.GenerateTasksUseCaseImpl;
import com.epm.ai.application.usecase.SummarizeProjectUseCaseImpl;
import com.epm.ai.domain.port.in.ChatWithProjectUseCase;
import com.epm.ai.domain.port.in.GenerateTasksUseCase;
import com.epm.ai.domain.port.in.SummarizeProjectUseCase;
import com.epm.ai.domain.port.out.AiCachePort;
import com.epm.ai.domain.port.out.AiEventPublisher;
import com.epm.ai.domain.port.out.AiModelPort;
import com.epm.ai.domain.port.out.AiTokenTracker;
import com.epm.ai.domain.port.out.ProjectContextPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration wiring use case implementations with their port dependencies.
 * Follows the same pattern as task-service UseCaseConfig.
 */
@Configuration
public class UseCaseConfig {

    @Bean
    public GenerateTasksUseCase generateTasksUseCase(AiModelPort modelPort,
                                                     ProjectContextPort contextPort,
                                                     AiEventPublisher eventPublisher,
                                                     AiCachePort cachePort,
                                                     AiTokenTracker tokenTracker) {
        return new GenerateTasksUseCaseImpl(modelPort, contextPort, eventPublisher, cachePort, tokenTracker);
    }

    @Bean
    public SummarizeProjectUseCase summarizeProjectUseCase(AiModelPort modelPort,
                                                           ProjectContextPort contextPort,
                                                           AiCachePort cachePort,
                                                           AiTokenTracker tokenTracker) {
        return new SummarizeProjectUseCaseImpl(modelPort, contextPort, cachePort, tokenTracker);
    }

    @Bean
    public ChatWithProjectUseCase chatWithProjectUseCase(AiModelPort modelPort,
                                                         ProjectContextPort contextPort,
                                                         AiTokenTracker tokenTracker) {
        return new ChatWithProjectUseCaseImpl(modelPort, contextPort, tokenTracker);
    }
}
