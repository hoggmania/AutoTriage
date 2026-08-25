package com.autotriage.common.engine;

import com.autotriage.common.model.ArtifactRef;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

@JsonPropertyOrder({"engineId", "runId", "source", "parameters"})
public final class EngineRequest {
    private final String engineId;
    private final String runId;
    private final ArtifactRef source;
    private final Map<String, String> parameters;

    @JsonCreator
    public EngineRequest(
            @JsonProperty("engineId") String engineId,
            @JsonProperty("runId") String runId,
            @JsonProperty("source") ArtifactRef source,
            @JsonProperty("parameters") Map<String, String> parameters) {
        this.engineId = requireText(engineId, "engineId");
        this.runId = requireText(runId, "runId");
        this.source = Objects.requireNonNull(source, "source");
        this.parameters = immutableMap(parameters, "parameters");
    }

    public String getEngineId() {
        return engineId;
    }

    public String getRunId() {
        return runId;
    }

    public ArtifactRef getSource() {
        return source;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    private static Map<String, String> immutableMap(Map<String, String> values, String name) {
        Objects.requireNonNull(values, name);
        TreeMap<String, String> copy = new TreeMap<>();
        values.forEach((key, value) -> copy.put(requireText(key, name + " key"), requireText(value, name + " value")));
        return Collections.unmodifiableMap(copy);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof EngineRequest that)) return false;
        return engineId.equals(that.engineId) && runId.equals(that.runId)
                && source.getUri().equals(that.source.getUri()) && source.getKind().equals(that.source.getKind())
                && parameters.equals(that.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(engineId, runId, source.getUri(), source.getKind(), parameters);
    }
}
