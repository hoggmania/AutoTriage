package com.autotriage.common.evidence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({"calibratedLevel", "calibration", "provenance", "rawZeroFalseSignals"})
public final class TriageEvidence {
    private final EvidenceLevel calibratedLevel;
    private final EvidenceCalibration calibration;
    private final EvidenceProvenance provenance;
    private final ZeroFalseSignals rawZeroFalseSignals;

    @JsonCreator
    public TriageEvidence(@JsonProperty("calibratedLevel") EvidenceLevel calibratedLevel,
                          @JsonProperty("calibration") EvidenceCalibration calibration,
                          @JsonProperty("provenance") EvidenceProvenance provenance,
                          @JsonProperty("rawZeroFalseSignals") ZeroFalseSignals rawZeroFalseSignals) {
        this.calibratedLevel = Objects.requireNonNull(calibratedLevel, "calibratedLevel");
        this.calibration = Objects.requireNonNull(calibration, "calibration");
        this.provenance = Objects.requireNonNull(provenance, "provenance");
        this.rawZeroFalseSignals = Objects.requireNonNull(rawZeroFalseSignals, "rawZeroFalseSignals");
        if (calibration.getLevel() != calibratedLevel) {
            throw new IllegalArgumentException("calibration level must match calibratedLevel");
        }
    }

    public EvidenceLevel getCalibratedLevel() { return calibratedLevel; }
    public EvidenceCalibration getCalibration() { return calibration; }
    public EvidenceProvenance getProvenance() { return provenance; }
    public ZeroFalseSignals getRawZeroFalseSignals() { return rawZeroFalseSignals; }
}
