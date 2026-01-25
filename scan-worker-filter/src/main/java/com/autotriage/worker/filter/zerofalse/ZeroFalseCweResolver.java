package com.autotriage.worker.filter.zerofalse;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ZeroFalseCweResolver {

    private static final Pattern CWE_PATTERN = Pattern.compile("cwe-?(\\d{1,4})", Pattern.CASE_INSENSITIVE);

    private ZeroFalseCweResolver() {
    }

    public static String resolve(JsonNode result, JsonNode run) {
        if (result == null) {
            return null;
        }
        String cwe = extractFromTags(result.path("properties").path("tags"));
        if (cwe != null) {
            return cwe;
        }
        cwe = extractFromText(result.path("ruleId").asText(null));
        if (cwe != null) {
            return cwe;
        }
        JsonNode rule = resolveRule(result, run);
        if (rule != null) {
            cwe = extractFromTags(rule.path("properties").path("tags"));
            if (cwe != null) {
                return cwe;
            }
            cwe = extractFromText(rule.path("id").asText(null));
            if (cwe != null) {
                return cwe;
            }
        }
        return extractFromText(result.path("message").path("text").asText(null));
    }

    private static JsonNode resolveRule(JsonNode result, JsonNode run) {
        if (run == null) {
            return null;
        }
        JsonNode rules = run.path("tool").path("driver").path("rules");
        if (rules == null || !rules.isArray()) {
            return null;
        }
        int ruleIndex = result.path("ruleIndex").asInt(-1);
        if (ruleIndex >= 0 && ruleIndex < rules.size()) {
            return rules.get(ruleIndex);
        }
        String ruleId = result.path("ruleId").asText(null);
        if (ruleId == null) {
            return null;
        }
        for (JsonNode rule : rules) {
            if (ruleId.equals(rule.path("id").asText(null))) {
                return rule;
            }
        }
        return null;
    }

    private static String extractFromTags(JsonNode tags) {
        if (tags == null || !tags.isArray()) {
            return null;
        }
        for (JsonNode tag : tags) {
            String candidate = extractFromText(tag.asText(null));
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static String extractFromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = CWE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String digits = matcher.group(1);
        if (digits.length() == 1) {
            digits = "00" + digits;
        } else if (digits.length() == 2) {
            digits = "0" + digits;
        }
        return "CWE-" + digits;
    }
}
