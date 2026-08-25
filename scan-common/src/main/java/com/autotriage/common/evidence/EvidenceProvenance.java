package com.autotriage.common.evidence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"engineId", "engineVersion", "provider", "model", "modelVersion", "promptVariant",
        "promptHash", "sourceHash", "sarifHash", "evaluatedAt", "source", "details"})
public final class EvidenceProvenance {
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private final String engineId;
    private final String engineVersion;
    private final String provider;
    private final String model;
    private final String modelVersion;
    private final String promptVariant;
    private final String promptHash;
    private final String sourceHash;
    private final String sarifHash;
    private final Instant evaluatedAt;
    private final String source;
    private final Map<String, String> details;

    /** Legacy engine-SPI constructor. Such provenance is intentionally incomplete for triage policy. */
    public EvidenceProvenance(String engineId, String engineVersion, String source, Map<String, String> details) {
        this(engineId, engineVersion, null, null, null, null, null, null, null, null, source, details);
    }

    public EvidenceProvenance(String engineId, String engineVersion, String provider, String model,
                              String modelVersion, String promptVariant, String promptHash, String sourceHash,
                              String sarifHash, Instant evaluatedAt) {
        this(engineId, engineVersion, provider, model, modelVersion, promptVariant, promptHash, sourceHash,
                sarifHash, evaluatedAt, "zerofalse", Map.of());
    }

    @JsonCreator
    public EvidenceProvenance(
            @JsonProperty("engineId") String engineId,
            @JsonProperty("engineVersion") String engineVersion,
            @JsonProperty("provider") String provider,
            @JsonProperty("model") String model,
            @JsonProperty("modelVersion") String modelVersion,
            @JsonProperty("promptVariant") String promptVariant,
            @JsonProperty("promptHash") String promptHash,
            @JsonProperty("sourceHash") String sourceHash,
            @JsonProperty("sarifHash") String sarifHash,
            @JsonProperty("evaluatedAt") Instant evaluatedAt,
            @JsonProperty("source") String source,
            @JsonProperty("details") Map<String, String> details) {
        this.engineId = requireText(engineId, "engineId");
        this.engineVersion = requireText(engineVersion, "engineVersion");
        this.provider = optionalText(provider);
        this.model = optionalText(model);
        this.modelVersion = optionalText(modelVersion);
        this.promptVariant = optionalText(promptVariant);
        this.promptHash = optionalHash(promptHash, "promptHash");
        this.sourceHash = optionalHash(sourceHash, "sourceHash");
        this.sarifHash = optionalHash(sarifHash, "sarifHash");
        this.evaluatedAt = evaluatedAt;
        this.source = requireText(source, "source");
        this.details = immutableMap(details == null ? Map.of() : details, "details");
    }

    public String getEngineId() { return engineId; }
    public String getEngineVersion() { return engineVersion; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public String getModelVersion() { return modelVersion; }
    public String getPromptVariant() { return promptVariant; }
    public String getPromptHash() { return promptHash; }
    public String getSourceHash() { return sourceHash; }
    public String getSarifHash() { return sarifHash; }
    public Instant getEvaluatedAt() { return evaluatedAt; }
    public String getSource() { return source; }
    public Map<String, String> getDetails() { return details; }

    @JsonIgnore
    public boolean isCompleteForPolicy() {
        return provider != null && model != null && modelVersion != null && promptVariant != null
                && promptHash != null && sourceHash != null && sarifHash != null && evaluatedAt != null;
    }

    private static String optionalHash(String value, String name) {
        String normalized = optionalText(value);
        if (normalized != null && !SHA256.matcher(normalized).matches()) {
            throw new IllegalArgumentException(name + " must be 64 lowercase hexadecimal characters");
        }
        return normalized;
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Map<String, String> immutableMap(Map<String, String> values, String name) {
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
        if (!(other instanceof EvidenceProvenance that)) return false;
        return engineId.equals(that.engineId) && engineVersion.equals(that.engineVersion)
                && Objects.equals(provider, that.provider) && Objects.equals(model, that.model)
                && Objects.equals(modelVersion, that.modelVersion) && Objects.equals(promptVariant, that.promptVariant)
                && Objects.equals(promptHash, that.promptHash) && Objects.equals(sourceHash, that.sourceHash)
                && Objects.equals(sarifHash, that.sarifHash) && Objects.equals(evaluatedAt, that.evaluatedAt)
                && source.equals(that.source) && details.equals(that.details);
    }

    @Override
    public int hashCode() {
        return Objects.hash(engineId, engineVersion, provider, model, modelVersion, promptVariant, promptHash,
                sourceHash, sarifHash, evaluatedAt, source, details);
    }
}
