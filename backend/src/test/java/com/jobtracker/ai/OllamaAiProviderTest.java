package com.jobtracker.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.ai.domain.AiProviderException;
import com.jobtracker.ai.domain.AnalysisRequest;
import com.jobtracker.ai.domain.AnalysisResult;
import com.jobtracker.ai.infrastructure.AiResponseParser;
import com.jobtracker.ai.infrastructure.AnalysisPrompt;
import com.jobtracker.ai.infrastructure.OllamaAiProvider;
import com.jobtracker.ai.infrastructure.OllamaProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the provider over real HTTP against a stub of Ollama's /api/chat endpoint,
 * so Spring AI's client, timeouts and error handling are covered without a running Ollama.
 */
class OllamaAiProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AnalysisRequest REQUEST =
        new AnalysisRequest("Java and Spring Boot developer", "Looking for Java, Spring Boot and Kafka");

    private HttpServer server;
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();

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
    void parsesModelAnswerIntoAnalysisResult() {
        respondWith(200, chatResponse("""
            {"matchScore": 67, "matchingSkills": ["Java", "Spring Boot"], "missingSkills": ["Kafka"],
             "strengths": ["Solid Java background"], "recommendations": ["Mention any Kafka work"],
             "summary": "Good fit apart from Kafka."}"""));

        AnalysisResult result = provider(Duration.ofSeconds(5)).analyze(REQUEST);

        assertThat(result.matchScore()).isEqualTo(67);
        assertThat(result.matchingSkills()).containsExactly("Java", "Spring Boot");
        assertThat(result.missingSkills()).containsExactly("Kafka");
        assertThat(result.summary()).isEqualTo("Good fit apart from Kafka.");
    }

    @Test
    void sendsConfiguredModelSchemaAndBothTexts() throws IOException {
        respondWith(200, chatResponse("{\"matchScore\": 50, \"summary\": \"ok\"}"));

        provider(Duration.ofSeconds(5)).analyze(REQUEST);

        JsonNode sent = MAPPER.readTree(lastRequestBody.get());
        assertThat(sent.path("model").asText()).isEqualTo("test-model");
        assertThat(sent.path("stream").asBoolean()).isFalse();
        assertThat(sent.path("format").path("required")).hasSize(6);
        assertThat(sent.path("options").path("num_ctx").asInt()).isEqualTo(8192);
        assertThat(sent.path("messages").get(0).path("content").asText()).isEqualTo(AnalysisPrompt.SYSTEM);
        assertThat(sent.path("messages").get(1).path("content").asText())
            .contains("<resume>", "Java and Spring Boot developer", "<job_description>", "Kafka");
    }

    @Test
    void malformedModelAnswerBecomesProviderException() {
        respondWith(200, chatResponse("Sorry, I cannot help with that."));

        assertThatThrownBy(() -> provider(Duration.ofSeconds(5)).analyze(REQUEST))
            .isInstanceOf(AiProviderException.class)
            .hasMessageContaining("did not contain a JSON object");
    }

    @Test
    void missingModelExplainsHowToPullIt() {
        respondWith(404, "{\"error\":\"model \\\"test-model\\\" not found, try pulling it first\"}");

        assertThatThrownBy(() -> provider(Duration.ofSeconds(5)).analyze(REQUEST))
            .isInstanceOf(AiProviderException.class)
            .hasMessage("Ollama model 'test-model' is not available; run 'ollama pull test-model'");
    }

    @Test
    void serverErrorBecomesProviderException() {
        respondWith(500, "{\"error\":\"out of memory\"}");

        assertThatThrownBy(() -> provider(Duration.ofSeconds(5)).analyze(REQUEST))
            .isInstanceOf(AiProviderException.class)
            .hasMessage("Ollama failed while running the analysis");
    }

    @Test
    void unreachableServerBecomesProviderException() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        OllamaProperties props = properties("http://127.0.0.1:" + closedPort, Duration.ofSeconds(5), 8192);

        assertThatThrownBy(() -> new OllamaAiProvider(props, parser()).analyze(REQUEST))
            .isInstanceOf(AiProviderException.class)
            .hasMessageContaining("not reachable at http://127.0.0.1:" + closedPort);
    }

    @Test
    void slowServerTimesOut() {
        server.createContext("/api/chat", exchange -> {
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
    void inputTooLargeForContextWindowIsRejectedWithoutCallingOllama() {
        respondWith(200, chatResponse("{\"matchScore\": 50, \"summary\": \"ok\"}"));
        OllamaProperties props = properties(baseUrl(), Duration.ofSeconds(5), 2048);
        AnalysisRequest huge = new AnalysisRequest("x".repeat(10_000), "Java developer");

        assertThatThrownBy(() -> new OllamaAiProvider(props, parser()).analyze(huge))
            .isInstanceOf(AiProviderException.class)
            .hasMessageContaining("OLLAMA_NUM_CTX");
        assertThat(lastRequestBody.get()).isNull();
    }

    // ------------------------------------------------------------------ //

    private OllamaAiProvider provider(Duration readTimeout) {
        return new OllamaAiProvider(properties(baseUrl(), readTimeout, 8192), parser());
    }

    private static OllamaProperties properties(String baseUrl, Duration readTimeout, int numCtx) {
        return new OllamaProperties(baseUrl, "test-model", Duration.ofSeconds(2), readTimeout, 0.2, numCtx, 1024);
    }

    private static AiResponseParser parser() {
        return new AiResponseParser(MAPPER);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void respondWith(int status, String body) {
        server.createContext("/api/chat", exchange -> {
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            send(exchange, status, body);
        });
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    /** A non-streaming /api/chat response as Ollama returns it. */
    private static String chatResponse(String content) {
        try {
            return MAPPER.writeValueAsString(MAPPER.createObjectNode()
                .put("model", "test-model")
                .put("created_at", "2026-10-14T10:00:00.000Z")
                .put("done", true)
                .put("done_reason", "stop")
                .<com.fasterxml.jackson.databind.node.ObjectNode>set("message", MAPPER.createObjectNode()
                    .put("role", "assistant")
                    .put("content", content)));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
