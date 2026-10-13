package com.jobtracker.ai.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Selects the active {@link AiProvider} from every provider bean on the classpath,
 * based on {@code app.ai.provider}. Resolution happens once at startup so a typo in
 * configuration fails the boot instead of the first analysis request.
 */
@Component
public class AiProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(AiProviderFactory.class);

    private final AiProvider activeProvider;

    public AiProviderFactory(List<AiProvider> providers,
                             @Value("${app.ai.provider:mock}") String configuredProvider) {
        Map<String, AiProvider> byName = providers.stream()
            .collect(Collectors.toMap(p -> normalise(p.name()), Function.identity(), (a, b) -> {
                throw new IllegalStateException("Duplicate AI provider name: " + a.name());
            }));

        String wanted = normalise(configuredProvider);
        this.activeProvider = byName.get(wanted);
        if (activeProvider == null) {
            throw new IllegalStateException("Unknown AI provider '" + configuredProvider
                + "'. Available: " + byName.keySet().stream().sorted().toList());
        }
        log.info("Using AI provider: {}", activeProvider.name());
    }

    public AiProvider getProvider() {
        return activeProvider;
    }

    private static String normalise(String name) {
        return name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
    }
}
