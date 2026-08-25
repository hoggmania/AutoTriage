package com.autotriage.common.engine;

import com.autotriage.common.evidence.Evidence;
import com.autotriage.common.model.ArtifactRef;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"engineId", "engineVersion", "outputs", "evidence"})
public final class EngineResult {
    private final String engineId;
    private final String engineVersion;
    private final List<ArtifactRef> outputs;
    private final List<Evidence> evidence;

    @JsonCreator
    public EngineResult(
            @JsonProperty("engineId") String engineId,
            @JsonProperty("engineVersion") String engineVersion,
            @JsonProperty("outputs") List<ArtifactRef> outputs,
            @JsonProperty("evidence") List<Evidence> evidence) {
        this.engineId = requireText(engineId, "engineId");
        this.engineVersion = requireText(engineVersion, "engineVersion");
        this.outputs = List.copyOf(Objects.requireNonNull(outputs, "outputs"));
        this.evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
    }

    public String getEngineId() { return engineId; }
    public String getEngineVersion() { return engineVersion; }
    public List<ArtifactRef> getOutputs() { return outputs; }
    public List<Evidence> getEvidence() { return evidence; }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof EngineResult that)) return false;
        if (!engineId.equals(that.engineId) || !engineVersion.equals(that.engineVersion)
                || !evidence.equals(that.evidence) || outputs.size() != that.outputs.size()) return false;
        for (int i = 0; i < outputs.size(); i++) {
            ArtifactRef left = outputs.get(i);
            ArtifactRef right = that.outputs.get(i);
            if (!left.getUri().equals(right.getUri()) || !left.getKind().equals(right.getKind())) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = Objects.hash(engineId, engineVersion, evidence);
        for (ArtifactRef output : outputs) hash = 31 * hash + Objects.hash(output.getUri(), output.getKind());
        return hash;
    }
}
