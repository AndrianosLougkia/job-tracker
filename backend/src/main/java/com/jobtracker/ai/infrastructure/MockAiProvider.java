package com.jobtracker.ai.infrastructure;

import com.jobtracker.ai.domain.AiProvider;
import com.jobtracker.ai.domain.AnalysisRequest;
import com.jobtracker.ai.domain.AnalysisResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Deterministic, offline provider for local development and automated tests.
 *
 * Scans both texts for a fixed vocabulary of technical skills. Skills the job
 * description mentions are split into matching (also in the resume) and missing;
 * the score is the percentage matched. The same input always yields the same
 * output, and list order follows the vocabulary order below.
 */
@Component
public class MockAiProvider implements AiProvider {

    public static final String NAME = "mock";

    /** Score returned when the job description mentions no known skill. */
    static final int NEUTRAL_SCORE = 50;

    private static final int MAX_RECOMMENDATIONS = 5;
    private static final int MAX_HIGHLIGHTED_SKILLS = 3;

    private static final List<String> SKILL_VOCABULARY = List.of(
        "Java", "Kotlin", "Python", "JavaScript", "TypeScript", "Golang", "C#", "C++",
        "Spring Boot", "Hibernate", "Node.js", "React", "Angular", "Vue",
        "SQL", "PostgreSQL", "MySQL", "MongoDB", "Redis",
        "Kafka", "RabbitMQ", "REST", "GraphQL", "Microservices",
        "Docker", "Kubernetes", "AWS", "Azure", "GCP", "Terraform",
        "CI/CD", "Git", "Linux", "JUnit"
    );

    // A skill counts only when not embedded in a longer alphanumeric token,
    // so "Java" does not match "JavaScript" and "SQL" does not match "PostgreSQL".
    // All-caps acronyms match case-sensitively so "the rest of" is not "REST".
    private static final List<Pattern> SKILL_PATTERNS = SKILL_VOCABULARY.stream()
        .map(skill -> Pattern.compile(
            "(?<![A-Za-z0-9])" + Pattern.quote(skill) + "(?![A-Za-z0-9+#])",
            skill.equals(skill.toUpperCase()) ? 0 : Pattern.CASE_INSENSITIVE))
        .toList();

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public AnalysisResult analyze(AnalysisRequest request) {
        List<String> required = skillsIn(request.jobDescription());
        List<String> offered = skillsIn(request.resumeText());

        List<String> matching = required.stream().filter(offered::contains).toList();
        List<String> missing = required.stream().filter(s -> !offered.contains(s)).toList();

        int score = required.isEmpty()
            ? NEUTRAL_SCORE
            : (int) Math.round(100.0 * matching.size() / required.size());

        return new AnalysisResult(
            score,
            matching,
            missing,
            strengths(matching, required, score),
            recommendations(matching, missing, required),
            summary(matching, required, score));
    }

    static List<String> skillsIn(String text) {
        List<String> found = new ArrayList<>();
        for (int i = 0; i < SKILL_VOCABULARY.size(); i++) {
            if (SKILL_PATTERNS.get(i).matcher(text).find()) {
                found.add(SKILL_VOCABULARY.get(i));
            }
        }
        return found;
    }

    private static List<String> strengths(List<String> matching, List<String> required, int score) {
        List<String> strengths = new ArrayList<>();
        if (matching.isEmpty()) return strengths;
        strengths.add("Covers " + matching.size() + " of " + required.size()
            + " skills listed in the job description");
        strengths.add("Relevant experience with " + joinFirst(matching));
        if (score >= 75) {
            strengths.add("Strong overall alignment with the role's technical requirements");
        }
        return strengths;
    }

    private static List<String> recommendations(List<String> matching, List<String> missing,
                                                List<String> required) {
        List<String> recommendations = new ArrayList<>();
        if (required.isEmpty()) {
            recommendations.add("Add the role's concrete technical requirements to the job "
                + "description for a more precise analysis");
            return recommendations;
        }
        missing.stream().limit(MAX_RECOMMENDATIONS).forEach(skill -> recommendations.add(
            "Highlight any experience with " + skill + ", or consider building it"));
        if (missing.isEmpty()) {
            recommendations.add("Quantify achievements involving " + joinFirst(matching)
                + " to stand out among similar candidates");
        }
        return recommendations;
    }

    private static String summary(List<String> matching, List<String> required, int score) {
        if (required.isEmpty()) {
            return "No recognised technical skills found in the job description; "
                + "returning a neutral score of " + NEUTRAL_SCORE + ".";
        }
        String fit = score >= 75 ? "Strong" : score >= 50 ? "Moderate" : "Weak";
        return fit + " match: the resume covers " + matching.size() + " of " + required.size()
            + " required skills (" + score + "%).";
    }

    private static String joinFirst(List<String> skills) {
        return String.join(", ", skills.subList(0, Math.min(MAX_HIGHLIGHTED_SKILLS, skills.size())));
    }
}
