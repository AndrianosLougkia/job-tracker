package com.jobtracker.ai.infrastructure;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.beta.messages.BetaJsonOutputFormat;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTextBlock;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.jobtracker.ai.domain.AiProvider;
import com.jobtracker.ai.domain.AiProviderException;
import com.jobtracker.ai.domain.AnalysisRequest;
import com.jobtracker.ai.domain.AnalysisResult;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InterruptedIOException;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Runs analyses on Claude through Anthropic's official Java SDK.
 *
 * Optional: without {@code ANTHROPIC_API_KEY} no client is created and no request
 * ever leaves the application; selecting this provider then fails each analysis with
 * a clear message instead of failing startup. The answer is constrained to
 * {@link AnalysisPrompt#RESPONSE_SCHEMA} with structured outputs and still goes
 * through {@link AiResponseParser}, like every other provider.
 */
@Component
public class ClaudeAiProvider implements AiProvider {

    public static final String NAME = "claude";

    /** Opts in to server-side refusal fallbacks, routed by refusal category. */
    static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private static final Logger log = LoggerFactory.getLogger(ClaudeAiProvider.class);

    private final ClaudeProperties properties;
    private final AiResponseParser parser;
    private final AnthropicClient client;

    public ClaudeAiProvider(ClaudeProperties properties, AiResponseParser parser) {
        this.properties = properties;
        this.parser = parser;
        this.client = properties.isConfigured() ? buildClient(properties) : null;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public AnalysisResult analyze(AnalysisRequest request) {
        if (client == null) {
            throw new AiProviderException("Claude is not configured; set ANTHROPIC_API_KEY or choose another AI_PROVIDER");
        }
        BetaMessage message = call(buildParams(request));

        BetaStopReason stopReason = message.stopReason().orElse(null);
        if (BetaStopReason.REFUSAL.equals(stopReason)) {
            throw new AiProviderException("Claude declined to analyse this resume and job description");
        }
        if (BetaStopReason.MAX_TOKENS.equals(stopReason)) {
            throw new AiProviderException("Claude's answer was cut off; raise CLAUDE_MAX_TOKENS");
        }

        // Only text blocks carry the answer; thinking blocks are skipped.
        String text = message.content().stream()
            .flatMap(block -> block.text().stream())
            .map(BetaTextBlock::text)
            .collect(Collectors.joining());
        return parser.parse(text);
    }

    private MessageCreateParams buildParams(AnalysisRequest request) {
        BetaJsonOutputFormat.Schema.Builder schema = BetaJsonOutputFormat.Schema.builder();
        AnalysisPrompt.RESPONSE_SCHEMA.forEach((key, value) -> schema.putAdditionalProperty(key, JsonValue.from(value)));

        return MessageCreateParams.builder()
            .model(properties.model())
            .maxTokens(properties.maxTokens())
            .system(AnalysisPrompt.SYSTEM)
            .addUserMessage(AnalysisPrompt.userMessage(request))
            .outputConfig(BetaOutputConfig.builder()
                .effort(BetaOutputConfig.Effort.of(properties.effort().strip().toLowerCase(Locale.ROOT)))
                .format(BetaJsonOutputFormat.builder().schema(schema.build()).build())
                .build())
            .addBeta(FALLBACK_BETA)
            .fallbacksDefault()
            .build();
    }

    // Most specific first; the SDK has already retried 429, 5xx and network failures.
    private BetaMessage call(MessageCreateParams params) {
        try {
            return client.beta().messages().create(params);
        } catch (UnauthorizedException | PermissionDeniedException e) {
            throw new AiProviderException("Claude rejected the API key; check ANTHROPIC_API_KEY", e);
        } catch (NotFoundException e) {
            throw new AiProviderException("Claude model '" + properties.model() + "' was not found; check CLAUDE_MODEL", e);
        } catch (RateLimitException e) {
            throw new AiProviderException("Claude rate limit reached; try again shortly", e);
        } catch (BadRequestException e) {
            throw new AiProviderException("Claude rejected the analysis request", e);
        } catch (InternalServerException e) {
            throw new AiProviderException("Claude is temporarily unavailable; try again shortly", e);
        } catch (AnthropicServiceException e) {
            String type = e.errorType().map(Object::toString).orElse("HTTP " + e.statusCode());
            throw new AiProviderException("Claude request failed (" + type + ")", e);
        } catch (AnthropicIoException e) {
            if (isTimeout(e)) {
                throw new AiProviderException("Claude did not respond within the configured timeout ("
                    + properties.timeout().toSeconds() + "s)", e);
            }
            throw new AiProviderException("Claude API is not reachable; check the network connection", e);
        } catch (RuntimeException e) {
            throw new AiProviderException("Claude request failed", e);
        }
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof InterruptedIOException) return true;
        }
        return false;
    }

    private static AnthropicClient buildClient(ClaudeProperties properties) {
        log.info("Claude provider configured with model {}", properties.model());
        // Explicit key and URL only: the SDK's fromEnv() would also pick up other credential sources.
        return AnthropicOkHttpClient.builder()
            .apiKey(properties.apiKey())
            .baseUrl(properties.baseUrl())
            .timeout(properties.timeout())
            .maxRetries(properties.maxRetries())
            .build();
    }

    @PreDestroy
    void close() {
        if (client != null) client.close();
    }
}
