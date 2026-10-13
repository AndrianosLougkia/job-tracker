package com.jobtracker.ai.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.ai.domain.AiProviderException;
import com.jobtracker.ai.domain.AnalysisResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns the raw text an LLM returns into a validated {@link AnalysisResult}.
 *
 * Tolerates the usual model quirks — markdown code fences, prose around the JSON
 * object, fractional scores, duplicate or blank list entries — and rejects anything
 * that is structurally wrong with an {@link AiProviderException}, whose message is
 * safe to persist (it never echoes the raw response).
 */
@Component
public class AiResponseParser {

    static final int MAX_ITEMS_PER_LIST = 20;
    static final int MAX_ITEM_LENGTH = 300;
    static final int MAX_SUMMARY_LENGTH = 2000;

    private final ObjectMapper objectMapper;

    public AiResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AnalysisResult parse(String rawResponse) {
        JsonNode root = readJsonObject(rawResponse);
        return new AnalysisResult(
            score(root),
            stringList(root, "matchingSkills"),
            stringList(root, "missingSkills"),
            stringList(root, "strengths"),
            stringList(root, "recommendations"),
            summary(root));
    }

    private JsonNode readJsonObject(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AiProviderException("AI provider returned an empty response");
        }
        // Models often wrap JSON in ```json fences or add a sentence before/after it.
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new AiProviderException("AI provider response did not contain a JSON object");
        }
        try {
            JsonNode node = objectMapper.readTree(raw.substring(start, end + 1));
            if (node == null || !node.isObject()) {
                throw new AiProviderException("AI provider response did not contain a JSON object");
            }
            return node;
        } catch (JsonProcessingException e) {
            throw new AiProviderException("AI provider returned malformed JSON", e);
        }
    }

    private static int score(JsonNode root) {
        JsonNode node = root.get("matchScore");
        if (node == null || node.isNull()) {
            throw new AiProviderException("AI provider response is missing 'matchScore'");
        }
        double value;
        if (node.isNumber()) {
            value = node.asDouble();
        } else if (node.isTextual()) {
            try {
                value = Double.parseDouble(node.asText().strip().replace("%", ""));
            } catch (NumberFormatException e) {
                throw new AiProviderException("AI provider returned a non-numeric 'matchScore'");
            }
        } else {
            throw new AiProviderException("AI provider returned a non-numeric 'matchScore'");
        }
        long rounded = Math.round(value);
        if (Double.isNaN(value) || rounded < AnalysisResult.MIN_SCORE || rounded > AnalysisResult.MAX_SCORE) {
            throw new AiProviderException("AI provider returned an out-of-range 'matchScore'");
        }
        return (int) rounded;
    }

    private static List<String> stringList(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) return List.of();
        if (!node.isArray()) {
            throw new AiProviderException("AI provider returned a non-list '" + field + "'");
        }
        Set<String> items = new LinkedHashSet<>();
        for (JsonNode element : node) {
            if (!element.isValueNode() || element.isNull()) continue;
            String text = truncate(element.asText().strip(), MAX_ITEM_LENGTH);
            if (!text.isEmpty()) items.add(text);
            if (items.size() == MAX_ITEMS_PER_LIST) break;
        }
        return new ArrayList<>(items);
    }

    private static String summary(JsonNode root) {
        JsonNode node = root.get("summary");
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw new AiProviderException("AI provider response is missing 'summary'");
        }
        return truncate(node.asText().strip(), MAX_SUMMARY_LENGTH);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
