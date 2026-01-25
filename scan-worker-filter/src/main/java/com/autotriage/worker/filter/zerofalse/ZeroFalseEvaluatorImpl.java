package com.autotriage.worker.filter.zerofalse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class ZeroFalseEvaluatorImpl implements ZeroFalseEvaluator {

    private static final Logger log = Logger.getLogger(ZeroFalseEvaluatorImpl.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final ZeroFalseAiService aiService;

    @Inject
    public ZeroFalseEvaluatorImpl(ZeroFalseAiService aiService) {
        this.aiService = aiService;
    }

    @Override
    public Optional<ZeroFalseVerdict> evaluate(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return Optional.empty();
        }
        try {
            String response = aiService.evaluate(prompt);
            return parseResponse(response);
        } catch (Exception e) {
            log.warnv("ZeroFalse evaluation failed: {0}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<ZeroFalseVerdict> parseResponse(String response) {
        if (response == null || response.isBlank()) {
            return Optional.empty();
        }
        String json = extractJson(response);
        if (json == null) {
            log.debug("ZeroFalse response missing JSON payload");
            return Optional.empty();
        }
        try {
            JsonNode node = mapper.readTree(json);
            String falsePositive = readField(node, "False Positive");
            String sanitization = readField(node, "Sanitization Found?");
            String attackFeasible = readField(node, "Attack Feasible?");
            String confidence = readField(node, "Confidence");
            Integer confidencePercent = parseConfidencePercent(
                    readField(node, "ConfidencePercent"),
                    confidence);
            boolean isFalsePositive = isYes(falsePositive);
            return Optional.of(new ZeroFalseVerdict(isFalsePositive, sanitization, attackFeasible, confidence, confidencePercent));
        } catch (Exception e) {
            log.debugv("ZeroFalse response parse failed: {0}", e.getMessage());
            return Optional.empty();
        }
    }

    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start < 0 || end < start) {
            return null;
        }
        return response.substring(start, end + 1);
    }

    private String readField(JsonNode node, String key) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String expected = normalizeKey(key);
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (normalizeKey(entry.getKey()).equals(expected)) {
                return entry.getValue().asText(null);
            }
        }
        return null;
    }

    private String normalizeKey(String value) {
        StringBuilder normalized = new StringBuilder();
        for (char c : value.toLowerCase().toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                normalized.append(c);
            }
        }
        return normalized.toString();
    }

    private boolean isYes(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.equals("yes") || normalized.equals("true");
    }

    private Integer parseConfidencePercent(String percentValue, String confidence) {
        Integer parsed = parseInt(percentValue);
        if (parsed != null) {
            return clampPercent(parsed);
        }
        if (confidence == null) {
            return null;
        }
        String normalized = confidence.trim().toLowerCase();
        return switch (normalized) {
            case "low" -> 20;
            case "medium" -> 50;
            case "high" -> 80;
            default -> null;
        };
    }

    private Integer parseInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim().replace("%", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer clampPercent(Integer value) {
        if (value == null) {
            return null;
        }
        if (value < 0) {
            return 0;
        }
        if (value > 100) {
            return 100;
        }
        return value;
    }
}
