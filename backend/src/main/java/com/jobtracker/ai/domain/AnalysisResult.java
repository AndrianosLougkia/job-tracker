package com.jobtracker.ai.domain;

import java.util.List;
import java.util.Objects;

/**
 * Structured, provider-agnostic analysis output.
 * The compact constructor normalises every instance, whichever provider built it
 * (or whether Jackson rebuilt it from the database): lists are never null,
 * immutable, and free of null entries; the score is always within 0..100.
 */
public record AnalysisResult(
    int matchScore,
    List<String> matchingSkills,
    List<String> missingSkills,
    List<String> strengths,
    List<String> recommendations,
    String summary
) {
    public static final int MIN_SCORE = 0;
    public static final int MAX_SCORE = 100;

    public AnalysisResult {
        if (matchScore < MIN_SCORE || matchScore > MAX_SCORE) {
            throw new IllegalArgumentException(
                "matchScore must be between " + MIN_SCORE + " and " + MAX_SCORE + ": " + matchScore);
        }
        matchingSkills  = clean(matchingSkills);
        missingSkills   = clean(missingSkills);
        strengths       = clean(strengths);
        recommendations = clean(recommendations);
        summary = summary == null ? "" : summary.strip();
    }

    private static List<String> clean(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(Objects::nonNull).toList();
    }
}
