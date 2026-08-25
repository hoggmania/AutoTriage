package com.autotriage.common.evidence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({"evidenceId", "level", "confidence", "rationale"})
public final class EvidenceAssessment {
    private final String evidenceId;
    private final EvidenceLevel level;
    private final double confidence;
    private final String rationale;

    @JsonCreator
    public EvidenceAssessment(
            @JsonProperty("evidenceId") String evidenceId,
            @JsonProperty("level") EvidenceLevel level,
            @JsonProperty("confidence") double confidence,
            @JsonProperty("rationale") String rationale) {
        this.evidenceId = requireText(evidenceId, "evidenceId");
        this.level = Objects.requireNonNull(level, "level");
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be finite and between 0 and 1");
        }
        this.confidence = confidence;
        this.rationale = requireText(rationale, "rationale");
    }

    public String getEvidenceId() { return evidenceId; }
    public EvidenceLevel getLevel() { return level; }
    public double getConfidence() { return confidence; }
    public String getRationale() { return rationale; }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof EvidenceAssessment that)) return false;
        return Double.compare(confidence, that.confidence) == 0 && evidenceId.equals(that.evidenceId)
                && level == that.level && rationale.equals(that.rationale);
    }

    @Override
    public int hashCode() {
        return Objects.hash(evidenceId, level, confidence, rationale);
    }
}
