package com.jobtracker.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobtracker.ai.domain.AiProviderException;
import com.jobtracker.ai.domain.AnalysisRequest;
import com.jobtracker.ai.domain.AnalysisResult;
import com.jobtracker.ai.infrastructure.AiResponseParser;
import com.jobtracker.ai.infrastructure.AnalysisPrompt;
import com.jobtracker.ai.infrastructure.ClaudeAiProvider;
import com.jobtracker.ai.infrastructure.ClaudeProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the provider through the real Anthropic SDK against a local stub of the
 * Messages API, so no API key, network access or billing is involved.
 */
class ClaudeAiProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AnalysisRequest REQUEST =
        new AnalysisRequest("Java and Spring Boot developer", "Looking for Java, Spring Boot and Kafka");
    private static final String VALID_ANSWER = """
        {"matchScore": 67, "matchingSkills": ["Java", "Spring Boot"], "missingSkills": ["Kafka"],
         "strengths": ["Solid Java background"], "recommendations": ["Mention any Kafka work"],
         "summary": "Good fit apart from Kafka."}""";

    private HttpServer server;
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();
    private final AtomicReference<String> lastApiKey = new AtomicReference<>();
    private final AtomicReference<String> lastBetaHeader = new AtomicReference<>();
    private final AtomicInteger requestCount = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void parsesStructuredAnswerAndSkipsThinkingBlocks() {
        respondWith(200, message("end_turn", VALID_ANSWER));

        AnalysisResult result = provider(Duration.ofSeconds(5)).analyze(REQUEST);

        assertThat(result.matchScore()).isEqualTo(67);
        assertThat(result.matchingSkills()).containsExactly("Java", "Spring Boot");
        assertThat(result.missingSkills()).containsExactly("Kafka");
        assertThat(result.summary()).isEqualTo("Good fit apart from Kafka.");
    }

    @Test
    void sendsModelSchemaEffortFallbacksAndApiKey() throws IOException {
        respondWith(200, message("end_turn", VALID_ANSWER));

        provider(Duration.ofSeconds(5)).analyze(REQUEST);

        JsonNode sent = MAPPER.readTree(lastRequestBody.get());
        assertThat(sent.path("model").asText()).isEqualTo("claude-test");
        assertThat(sent.path("max_tokens").asLong()).isEqualTo(16000);
        assertThat(sent.path("system").asText()).isEqualTo(AnalysisPrompt.SYSTEM);
        assertThat(sent.path("messages").get(0).path("content").asText())
            .contains("<resume>", "Java and Spring Boot developer", "<job_description>", "Kafka");
        assertThat(sent.path("output_config").path("effort").asText()).isEqualTo("medium");
        assertThat(sent.path("output_config").path("format").path("type").asText()).isEqualTo("json_schema");
        assertThat(sent.path("output_config").path("format").path("schema").path("additionalProperties").asBoolean(true))
            .isFalse();
        assertThat(sent.path("fallbacks").asText()).isEqualTo("default");
        assertThat(sent.has("temperature")).isFalse();
        assertThat(lastBetaHeader.get()).contains("server-side-fallback-2026-07-01");
        assertThat(lastApiKey.get()).isEqualTo("test-key");
    }

    @Test
    void notConfiguredFailsWithoutAnyRequest() {
        ClaudeProperties props = properties("", Duration.ofSeconds(5));

        assertThatThrownBy(() -> new ClaudeAiProvider(props, parser()).analyze(REQUEST))
            .isInstanceOf(AiProviderException.class)
            .hasMessageContaining("set ANTHROPIC_API_KEY");
        assertThat(requestCount).hasValue(0);
    }

    @Test
    void refusalBecomesProviderException() {
        respondWith(200, message("refusal", ""));

        assertThatThrownBy(() -> provider(Duration.ofSeconds(5)).analyze(REQUEST))
            .isInstanceOf(AiProviderException.class)
            .hasMessageContaining("declined");
    }

    @Test
    void truncatedAnswerBecomesProviderException() {
        respondWith(200, message("max_tokens", "{\"matchScore\": 6"));

        assertThatThrownBy(() -> provider(Duration.ofSeconds(5)).analyze(REQUEST))
            .isInstanceOf(AiProviderException.class)
            .hasMessageContaining("CLAUDE_MAX_TOKENS");
    }

    @Test
    void invalidApiKeyIsReported() {
        respondWith(401, error("authentication_error", "invalid x-api-key"));

        assertThatThrownBy(() -> provider(Duration.ofSeconds(5)).analyze(REQUEST))
            .isInstanceOf(AiProviderException.class)
            .hasMessage("Claude rejected the API key; check ANTHROPIC_API_KEY");
    }

    @Test
    void unknownModelIsReported() {
        respondWith(404, error("not_found_error", "model: claude-test"));

        assertThatThrownBy(() -> provider(Duration.ofSeconds(5)).analyze(REQUEST))
            .isInstanceOf(AiProviderException.class)
            .hasMessage("Claude model 'claude-test' was not found; check CLAUDE_MODEL");
    }

    @Test
    void overloadIsReportedWithoutLeakingDetails() {
        respondWith(529, error("overloaded_error", "Overloaded"));

        assertThatThrownBy(() -> provider(Duration.ofSeconds(5)).analyze(REQUEST))
            .isInstanceOf(AiProviderException.class)
            .hasMessageStartingWith("Claude");
    }

    @Test
    void slowApiTimesOut() {
        server.createContext("/v1/messages", exchange -> {
            requestCount.incrementAndGet();
            try {
                Thread.sleep(3_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });

        assertThatThrownBy(() -> provider(Duration.ofMillis(300)).analyze(REQUEST))
            .isInstanceOf(AiProviderException.class)
            .hasMessageContaining("did not respond within the configured timeout");
    }

    @Test
    void propertiesNeverPrintTheApiKey() {
        assertThat(properties("sk-ant-secret", Duration.ofSeconds(5)).toString())
            .doesNotContain("sk-ant-secret")
            .contains("apiKey=****");
    }

    // ------------------------------------------------------------------ //

    private ClaudeAiProvider provider(Duration timeout) {
        return new ClaudeAiProvider(properties("test-key", timeout), parser());
    }

    private ClaudeProperties properties(String apiKey, Duration timeout) {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        // No retries, so failure tests make exactly one request.
        return new ClaudeProperties(apiKey, baseUrl, "claude-test", "medium", 16000, timeout, 0);
    }

    private static AiResponseParser parser() {
        return new AiResponseParser(MAPPER);
    }

    private void respondWith(int status, String body) {
        server.createContext("/v1/messages", exchange -> {
            requestCount.incrementAndGet();
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastApiKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            lastBetaHeader.set(exchange.getRequestHeaders().getFirst("anthropic-beta"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    /** A Messages API response with an (empty) thinking block followed by the text answer. */
    private static String message(String stopReason, String text) {
        ObjectNode root = MAPPER.createObjectNode()
            .put("id", "msg_test")
            .put("type", "message")
            .put("role", "assistant")
            .put("model", "claude-test")
            .put("stop_reason", stopReason)
            .putNull("stop_sequence");
        root.putArray("content")
            .add(MAPPER.createObjectNode().put("type", "thinking").put("thinking", "").put("signature", "sig"))
            .add(MAPPER.createObjectNode().put("type", "text").put("text", text));
        root.putObject("usage").put("input_tokens", 100).put("output_tokens", 50);
        return root.toString();
    }

    private static String error(String type, String message) {
        ObjectNode root = MAPPER.createObjectNode().put("type", "error");
        root.putObject("error").put("type", type).put("message", message);
        return root.toString();
    }
}
