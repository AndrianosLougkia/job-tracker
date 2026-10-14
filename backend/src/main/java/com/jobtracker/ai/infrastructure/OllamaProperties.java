package com.jobtracker.ai.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Settings for {@link OllamaAiProvider}, bound from {@code app.ai.ollama.*}.
 * Defaults suit a laptop running small models on the CPU, where a single analysis
 * can take a minute or more.
 *
 * @param baseUrl        Ollama server URL
 * @param model          model tag, which must already be pulled ({@code ollama pull <model>})
 * @param connectTimeout how long to wait for a connection; keeps "Ollama is not running" fast
 * @param readTimeout    how long to wait for the full answer
 * @param temperature    sampling temperature; low values keep scores stable between runs
 * @param numCtx         context window in tokens; Ollama's own default (2048) is too small
 *                       for a resume plus a job description
 * @param numPredict     maximum tokens in the answer
 */
@ConfigurationProperties("app.ai.ollama")
public record OllamaProperties(
    @DefaultValue("http://localhost:11434") String baseUrl,
    @DefaultValue("qwen2.5:3b") String model,
    @DefaultValue("5s") Duration connectTimeout,
    @DefaultValue("180s") Duration readTimeout,
    @DefaultValue("0.2") double temperature,
    @DefaultValue("8192") int numCtx,
    @DefaultValue("1024") int numPredict
) {}
