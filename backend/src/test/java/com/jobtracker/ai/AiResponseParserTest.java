package com.jobtracker.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.ai.domain.AiProviderException;
import com.jobtracker.ai.domain.AnalysisResult;
import com.jobtracker.ai.infrastructure.AiResponseParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Canned LLM responses, as Stage 7 providers will receive them. */
class AiResponseParserTest {

    private static final String VALID = """
        {
          "matchScore": 78,
          "matchingSkills": ["Java", "Spring Boot", "REST APIs"],
          "missingSkills": ["Kubernetes", "Go"],
          "strengths": ["Strong backend foundation"],
          "recommendations": ["Add cloud deployment section"],
          "summary": "Strong candidate for backend roles; some cloud gaps."
        }
        """;

    private final AiResponseParser parser = new AiResponseParser(new ObjectMapper());

    @Test
    void parsesWellFormedResponse() {
        AnalysisResult result = parser.parse(VALID);

        assertThat(result.matchScore()).isEqualTo(78);
        assertThat(result.matchingSkills()).containsExactly("Java", "Spring Boot", "REST APIs");
        assertThat(result.missingSkills()).containsExactly("Kubernetes", "Go");
        assertThat(result.strengths()).containsExactly("Strong backend foundation");
        assertThat(result.recommendations()).containsExactly("Add cloud deployment section");
        assertThat(result.summary()).isEqualTo("Strong candidate for backend roles; some cloud gaps.");
    }

    @Test
    void stripsMarkdownFencesAndSurroundingProse() {
        String raw = "Here is the analysis you asked for:\n```json\n" + VALID + "```\nLet me know!";
        assertThat(parser.parse(raw).matchScore()).isEqualTo(78);
    }

    @Test
    void roundsFractionalAndAcceptsNumericStringScores() {
        assertThat(parser.parse("{\"matchScore\": 77.6, \"summary\": \"s\"}").matchScore()).isEqualTo(78);
        assertThat(parser.parse("{\"matchScore\": \"65%\", \"summary\": \"s\"}").matchScore()).isEqualTo(65);
    }

    @Test
    void missingListsBecomeEmpty() {
        AnalysisResult result = parser.parse("{\"matchScore\": 10, \"summary\": \"s\"}");
        assertThat(result.matchingSkills()).isEmpty();
        assertThat(result.missingSkills()).isEmpty();
        assertThat(result.strengths()).isEmpty();
        assertThat(result.recommendations()).isEmpty();
    }

    @Test
    void cleansListEntries() {
        AnalysisResult result = parser.parse("""
            {"matchScore": 50, "summary": "s",
             "matchingSkills": [" Java ", "Java", "", null, {"nested": true}, 42]}
            """);
        assertThat(result.matchingSkills()).containsExactly("Java", "42");
    }

    @Test
    void capsListSizeAndItemLength() {
        String longItem = "x".repeat(1000);
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < 50; i++) items.append(i == 0 ? "" : ",").append("\"skill").append(i).append('"');
        AnalysisResult result = parser.parse("{\"matchScore\": 50, \"summary\": \"s\", "
            + "\"strengths\": [\"" + longItem + "\"], \"matchingSkills\": [" + items + "]}");

        assertThat(result.matchingSkills()).hasSize(20);
        assertThat(result.strengths().get(0)).hasSize(300);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "   ",
        "I'm sorry, I can't help with that.",
        "[1, 2, 3]",
        "{\"matchScore\": 50, \"summary\": ",
    })
    void rejectsResponsesWithoutAJsonObject(String raw) {
        assertThatThrownBy(() -> parser.parse(raw)).isInstanceOf(AiProviderException.class);
    }

    @Test
    void rejectsMissingScore() {
        assertThatThrownBy(() -> parser.parse("{\"summary\": \"s\"}"))
            .isInstanceOf(AiProviderException.class)
            .hasMessageContaining("matchScore");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "101", "\"high\"", "true", "[80]"})
    void rejectsInvalidScores(String score) {
        assertThatThrownBy(() -> parser.parse("{\"matchScore\": " + score + ", \"summary\": \"s\"}"))
            .isInstanceOf(AiProviderException.class)
            .hasMessageContaining("matchScore");
    }

    @Test
    void rejectsNonListSkills() {
        assertThatThrownBy(() -> parser.parse("{\"matchScore\": 5, \"summary\": \"s\", \"missingSkills\": \"Go\"}"))
            .isInstanceOf(AiProviderException.class)
            .hasMessageContaining("missingSkills");
    }

    @Test
    void rejectsMissingSummary() {
        assertThatThrownBy(() -> parser.parse("{\"matchScore\": 5, \"summary\": \"  \"}"))
            .isInstanceOf(AiProviderException.class)
            .hasMessageContaining("summary");
    }

    @Test
    void errorMessagesNeverEchoTheRawResponse() {
        String secretish = "{\"matchScore\": \"resume of Jane Doe, phone 555-0100\", \"summary\": \"s\"}";
        assertThatThrownBy(() -> parser.parse(secretish))
            .isInstanceOf(AiProviderException.class)
            .message().doesNotContain("Jane").doesNotContain("555");
    }
}
