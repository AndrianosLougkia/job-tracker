package com.jobtracker.ai.infrastructure;

import com.jobtracker.ai.domain.AiProvider;
import com.jobtracker.ai.domain.AiProviderException;
import com.jobtracker.ai.domain.AnalysisRequest;
import com.jobtracker.ai.domain.AnalysisResult;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Locale;

/**
 * Runs analyses on a local model served by Ollama, through Spring AI's Ollama client.
 *
 * Creating this bean never contacts Ollama, so the application starts normally when
 * Ollama is not running; the failure surfaces as an {@link AiProviderException} on
 * the first analysis instead. Calls are not retried: a stopped server or a missing
 * model will not fix itself, and a CPU-bound model is already slow.
 */
@Component
public class OllamaAiProvider implements AiProvider {

    public static final String NAME = "ollama";

    /** Conservative characters-per-token estimate used to check the context window up front. */
    static final int CHARS_PER_TOKEN_ESTIMATE = 3;

    private final ChatModel chatModel;
    private final OllamaProperties properties;
    private final AiResponseParser parser;

    @Autowired
    public OllamaAiProvider(OllamaProperties properties, AiResponseParser parser) {
        this(buildChatModel(properties), properties, parser);
    }

    OllamaAiProvider(ChatModel chatModel, OllamaProperties properties, AiResponseParser parser) {
        this.chatModel = chatModel;
        this.properties = properties;
        this.parser = parser;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public AnalysisResult analyze(AnalysisRequest request) {
        String userMessage = AnalysisPrompt.userMessage(request);
        requireFitsContextWindow(userMessage);

        Prompt prompt = new Prompt(List.of(new SystemMessage(AnalysisPrompt.SYSTEM), new UserMessage(userMessage)));
        ChatResponse response = call(prompt);

        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new AiProviderException("Ollama returned an empty response");
        }
        return parser.parse(response.getResult().getOutput().getText());
    }

    private ChatResponse call(Prompt prompt) {
        try {
            return chatModel.call(prompt);
        } catch (ResourceAccessException e) {
            if (isTimeout(e)) {
                throw new AiProviderException("Ollama did not respond within the configured timeout ("
                    + properties.readTimeout().toSeconds() + "s); try a smaller model or raise OLLAMA_READ_TIMEOUT", e);
            }
            throw new AiProviderException("Ollama is not reachable at " + properties.baseUrl()
                + "; is it running?", e);
        } catch (NonTransientAiException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase(Locale.ROOT).contains("not found")) {
                throw new AiProviderException("Ollama model '" + properties.model()
                    + "' is not available; run 'ollama pull " + properties.model() + "'", e);
            }
            throw new AiProviderException("Ollama rejected the analysis request", e);
        } catch (TransientAiException e) {
            throw new AiProviderException("Ollama failed while running the analysis", e);
        } catch (AiProviderException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new AiProviderException("Ollama request failed", e);
        }
    }

    /**
     * Ollama silently drops the start of a prompt that exceeds the context window,
     * which would cut off the instructions and produce a meaningless answer.
     */
    private void requireFitsContextWindow(String userMessage) {
        int estimatedTokens = (AnalysisPrompt.SYSTEM.length() + userMessage.length()) / CHARS_PER_TOKEN_ESTIMATE;
        if (estimatedTokens + properties.numPredict() > properties.numCtx()) {
            throw new AiProviderException("Resume and job description are too long for the Ollama context window ("
                + properties.numCtx() + " tokens); shorten them or raise OLLAMA_NUM_CTX");
        }
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) return true;
        }
        return false;
    }

    private static ChatModel buildChatModel(OllamaProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout())
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        OllamaApi api = OllamaApi.builder()
            .baseUrl(properties.baseUrl())
            .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
            .build();

        return OllamaChatModel.builder()
            .ollamaApi(api)
            .defaultOptions(OllamaChatOptions.builder()
                .model(properties.model())
                .format(AnalysisPrompt.RESPONSE_SCHEMA)
                .temperature(properties.temperature())
                .numCtx(properties.numCtx())
                .numPredict(properties.numPredict())
                .build())
            .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
            .build();
    }
}
