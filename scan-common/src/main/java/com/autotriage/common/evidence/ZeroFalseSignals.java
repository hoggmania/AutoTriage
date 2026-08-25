package com.autotriage.common.evidence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"falsePositive", "sanitizationFound", "attackFeasible", "confidence"})
public final class ZeroFalseSignals {
    private final Boolean falsePositive;
    private final String sanitizationFound;
    private final String attackFeasible;
    private final String confidence;

    @JsonCreator
    public ZeroFalseSignals(@JsonProperty("falsePositive") Boolean falsePositive,
                            @JsonProperty("sanitizationFound") String sanitizationFound,
                            @JsonProperty("attackFeasible") String attackFeasible,
                            @JsonProperty("confidence") String confidence) {
        this.falsePositive = falsePositive;
        this.sanitizationFound = sanitizationFound;
        this.attackFeasible = attackFeasible;
        this.confidence = confidence;
    }

    public Boolean getFalsePositive() { return falsePositive; }
    public String getSanitizationFound() { return sanitizationFound; }
    public String getAttackFeasible() { return attackFeasible; }
    public String getConfidence() { return confidence; }
}
