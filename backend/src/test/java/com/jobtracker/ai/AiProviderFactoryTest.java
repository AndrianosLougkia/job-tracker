package com.jobtracker.ai;

import com.jobtracker.ai.domain.AiProvider;
import com.jobtracker.ai.domain.AiProviderFactory;
import com.jobtracker.ai.domain.AnalysisRequest;
import com.jobtracker.ai.domain.AnalysisResult;
import com.jobtracker.ai.infrastructure.MockAiProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiProviderFactoryTest {

    private final AiProvider mock = new MockAiProvider();
    private final AiProvider other = new StubProvider("Other");

    @Test
    void selectsConfiguredProvider() {
        AiProviderFactory factory = new AiProviderFactory(List.of(mock, other), "other");
        assertThat(factory.getProvider()).isSameAs(other);
    }

    @Test
    void selectionIgnoresCaseAndWhitespace() {
        AiProviderFactory factory = new AiProviderFactory(List.of(mock, other), "  MOCK ");
        assertThat(factory.getProvider()).isSameAs(mock);
    }

    @Test
    void unknownProviderFailsFastListingAvailableOptions() {
        assertThatThrownBy(() -> new AiProviderFactory(List.of(mock, other), "gpt"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unknown AI provider 'gpt'")
            .hasMessageContaining("[mock, other]");
    }

    @Test
    void blankProviderFailsFast() {
        assertThatThrownBy(() -> new AiProviderFactory(List.of(mock), ""))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void duplicateProviderNamesAreRejected() {
        assertThatThrownBy(() -> new AiProviderFactory(List.of(mock, new StubProvider("mock")), "mock"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate");
    }

    private record StubProvider(String name) implements AiProvider {
        @Override public AnalysisResult analyze(AnalysisRequest request) {
            return new AnalysisResult(0, null, null, null, null, "stub");
        }
    }
}
