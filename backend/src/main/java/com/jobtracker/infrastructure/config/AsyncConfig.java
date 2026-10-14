package com.jobtracker.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Executor for background AI analyses.
 *
 * Bounded on purpose: the pool size caps how many AI provider calls run at once
 * (protecting rate limits, cost and a CPU-bound Ollama), and the queue caps how
 * much work can wait. When both are full, new analyses are rejected and stored
 * as FAILED instead of piling up in memory.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String ANALYSIS_EXECUTOR = "analysisExecutor";

    @Bean(name = ANALYSIS_EXECUTOR)
    public ThreadPoolTaskExecutor analysisExecutor(
            @Value("${app.analysis.executor.core-size:4}") int coreSize,
            @Value("${app.analysis.executor.max-size:8}") int maxSize,
            @Value("${app.analysis.executor.queue-capacity:100}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("analysis-async-");
        // Let running analyses finish on shutdown; anything still unfinished is
        // marked FAILED by StaleAnalysisRecovery on the next start.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
