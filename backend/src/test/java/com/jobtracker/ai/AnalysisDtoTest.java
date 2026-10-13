package com.jobtracker.ai;

import com.jobtracker.ai.domain.AnalysisRequest;
import com.jobtracker.ai.domain.AnalysisResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisDtoTest {

    @Test
    void resultNormalisesNullsAndIsImmutable() {
        List<String> skills = new ArrayList<>(Arrays.asList("Java", null));
        AnalysisResult result = new AnalysisResult(40, skills, null, null, null, null);

        assertThat(result.matchingSkills()).containsExactly("Java");
        assertThat(result.missingSkills()).isEmpty();
        assertThat(result.summary()).isEmpty();
        assertThatThrownBy(() -> result.matchingSkills().add("Go"))
            .isInstanceOf(UnsupportedOperationException.class);

        skills.add("Kotlin"); // defensive copy: caller's list must not leak in
        assertThat(result.matchingSkills()).containsExactly("Java");
    }

    @Test
    void resultRejectsOutOfRangeScore() {
        assertThatThrownBy(() -> new AnalysisResult(101, null, null, null, null, "s"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AnalysisResult(-1, null, null, null, null, "s"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requestRejectsBlankInputs() {
        assertThatThrownBy(() -> new AnalysisRequest(" ", "job"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AnalysisRequest("resume", null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requestToStringDoesNotExposeContent() {
        String text = new AnalysisRequest("Jane Doe, jane@example.com", "Senior role").toString();
        assertThat(text).doesNotContain("Jane").contains("chars");
    }
}
