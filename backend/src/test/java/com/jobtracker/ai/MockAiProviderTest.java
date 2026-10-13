package com.jobtracker.ai;

import com.jobtracker.ai.domain.AnalysisRequest;
import com.jobtracker.ai.domain.AnalysisResult;
import com.jobtracker.ai.infrastructure.MockAiProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockAiProviderTest {

    private static final String RESUME =
        "Backend engineer. 5 years of Java and Spring Boot, PostgreSQL, Docker and REST APIs.";
    private static final String JOB =
        "We need Java, Spring Boot, Kubernetes, PostgreSQL and AWS experience.";

    private final MockAiProvider provider = new MockAiProvider();

    @Test
    void nameIsMock() {
        assertThat(provider.name()).isEqualTo("mock");
    }

    @Test
    void producesExpectedStructuredResult() {
        AnalysisResult result = provider.analyze(new AnalysisRequest(RESUME, JOB));

        assertThat(result.matchScore()).isEqualTo(60);
        assertThat(result.matchingSkills()).containsExactly("Java", "Spring Boot", "PostgreSQL");
        assertThat(result.missingSkills()).containsExactly("Kubernetes", "AWS");
        assertThat(result.strengths()).containsExactly(
            "Covers 3 of 5 skills listed in the job description",
            "Relevant experience with Java, Spring Boot, PostgreSQL");
        assertThat(result.recommendations()).containsExactly(
            "Highlight any experience with Kubernetes, or consider building it",
            "Highlight any experience with AWS, or consider building it");
        assertThat(result.summary()).isEqualTo(
            "Moderate match: the resume covers 3 of 5 required skills (60%).");
    }

    @Test
    void isDeterministic() {
        AnalysisRequest request = new AnalysisRequest(RESUME, JOB);
        assertThat(provider.analyze(request)).isEqualTo(provider.analyze(request));
    }

    @Test
    void fullMatchScores100WithStrongSummary() {
        AnalysisResult result = provider.analyze(new AnalysisRequest(RESUME, "Java and Docker."));

        assertThat(result.matchScore()).isEqualTo(100);
        assertThat(result.missingSkills()).isEmpty();
        assertThat(result.strengths()).contains("Strong overall alignment with the role's technical requirements");
        assertThat(result.recommendations()).containsExactly(
            "Quantify achievements involving Java, Docker to stand out among similar candidates");
        assertThat(result.summary()).startsWith("Strong match");
    }

    @Test
    void noOverlapScoresZero() {
        AnalysisResult result = provider.analyze(
            new AnalysisRequest("Graphic designer skilled in Figma.", "Requires Kotlin and Kafka."));

        assertThat(result.matchScore()).isZero();
        assertThat(result.matchingSkills()).isEmpty();
        assertThat(result.missingSkills()).containsExactly("Kotlin", "Kafka");
        assertThat(result.strengths()).isEmpty();
        assertThat(result.summary()).startsWith("Weak match");
    }

    @Test
    void jobWithoutRecognisedSkillsGetsNeutralScore() {
        AnalysisResult result = provider.analyze(
            new AnalysisRequest(RESUME, "Friendly team, great snacks, flexible hours."));

        assertThat(result.matchScore()).isEqualTo(50);
        assertThat(result.matchingSkills()).isEmpty();
        assertThat(result.missingSkills()).isEmpty();
        assertThat(result.recommendations()).hasSize(1);
        assertThat(result.summary()).contains("No recognised technical skills");
    }

    @Test
    void skillMatchingRespectsTokenBoundaries() {
        // "JavaScript" is not "Java", "PostgreSQL" is not "SQL", "GitHub" is not "Git",
        // and the English word "rest" is not the "REST" acronym.
        AnalysisResult result = provider.analyze(new AnalysisRequest(
            "JavaScript developer using PostgreSQL and GitHub",
            "Java, SQL, Git, and REST. The rest is up to you."));

        assertThat(result.missingSkills()).containsExactly("Java", "SQL", "REST", "Git");
        assertThat(result.matchingSkills()).isEmpty();
    }

    @Test
    void matchingIsCaseInsensitiveForNonAcronyms() {
        AnalysisResult result = provider.analyze(
            new AnalysisRequest("java, spring boot, C++", "JAVA, SPRING BOOT and C++"));

        assertThat(result.matchingSkills()).isEqualTo(List.of("Java", "C++", "Spring Boot"));
    }
}
