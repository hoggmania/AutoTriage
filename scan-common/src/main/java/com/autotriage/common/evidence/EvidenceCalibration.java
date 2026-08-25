package com.autotriage.common.evidence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({"calibratorId", "version", "profile", "level", "rawScore"})
public final class EvidenceCalibration {
    private final String calibratorId;
    private final String version;
    private final String profile;
    private final EvidenceLevel level;
    private final Double rawScore;

    @JsonCreator
    public EvidenceCalibration(@JsonProperty("calibratorId") String calibratorId,
                               @JsonProperty("version") String version,
                               @JsonProperty("profile") String profile,
                               @JsonProperty("level") EvidenceLevel level,
                               @JsonProperty("rawScore") Double rawScore) {
        this.calibratorId = requireText(calibratorId, "calibratorId");
        this.version = requireText(version, "version");
        this.profile = requireText(profile, "profile");
        this.level = Objects.requireNonNull(level, "level");
        if (rawScore != null && (!Double.isFinite(rawScore) || rawScore < 0.0 || rawScore > 1.0)) {
            throw new IllegalArgumentException("rawScore must be finite and between 0 and 1");
        }
        this.rawScore = rawScore;
    }

    public String getCalibratorId() { return calibratorId; }
    public String getVersion() { return version; }
    public String getProfile() { return profile; }
    public EvidenceLevel getLevel() { return level; }
    public Double getRawScore() { return rawScore; }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
