package com.jobtracker.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.ai.domain.AnalysisRequest;
import com.jobtracker.ai.domain.AnalysisResult;
import com.jobtracker.ai.infrastructure.AiResponseParser;
import com.jobtracker.ai.infrastructure.OllamaAiProvider;
import com.jobtracker.ai.infrastructure.OllamaProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs one real analysis against a local Ollama. Skipped unless OLLAMA_LIVE_TEST=true,
 * so the default suite never needs Ollama. Uses OLLAMA_BASE_URL and OLLAMA_MODEL when set.
 *
 * <pre>OLLAMA_LIVE_TEST=true mvn test -Dtest=OllamaLiveTest</pre>
 */
@EnabledIfEnvironmentVariable(named = "OLLAMA_LIVE_TEST", matches = "true")
class OllamaLiveTest {

    @Test
    void analysesResumeAgainstJobDescription() {
        OllamaProperties props = new OllamaProperties(
            envOr("OLLAMA_BASE_URL", "http://localhost:11434"),
            envOr("OLLAMA_MODEL", "qwen2.5:3b"),
            Duration.ofSeconds(5), Duration.ofMinutes(5), 0.2, 8192, 1024);
        OllamaAiProvider provider = new OllamaAiProvider(props, new AiResponseParser(new ObjectMapper()));

        AnalysisResult result = provider.analyze(new AnalysisRequest(
            "Backend developer with 4 years of Java 17, Spring Boot, PostgreSQL and Docker. "
                + "Built REST APIs and wrote JUnit tests.",
            "We are hiring a Java backend engineer. Required: Java, Spring Boot, PostgreSQL, Kafka, Kubernetes."));

        assertThat(result.matchScore()).isBetween(0, 100);
        assertThat(result.summary()).isNotBlank();
        System.out.println("Ollama live result: " + result);
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
