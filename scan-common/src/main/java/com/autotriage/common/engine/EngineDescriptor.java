package com.autotriage.common.engine;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

@JsonPropertyOrder({"id", "version", "inputKinds", "outputKinds"})
public final class EngineDescriptor {
    private final String id;
    private final String version;
    private final Set<String> inputKinds;
    private final Set<String> outputKinds;

    @JsonCreator
    public EngineDescriptor(
            @JsonProperty("id") String id,
            @JsonProperty("version") String version,
            @JsonProperty("inputKinds") Set<String> inputKinds,
            @JsonProperty("outputKinds") Set<String> outputKinds) {
        this.id = requireIdentifier(id, "id");
        this.version = requireText(version, "version");
        this.inputKinds = immutableKinds(inputKinds, "inputKinds");
        this.outputKinds = immutableKinds(outputKinds, "outputKinds");
    }

    public String getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public Set<String> getInputKinds() {
        return inputKinds;
    }

    public Set<String> getOutputKinds() {
        return outputKinds;
    }

    private static Set<String> immutableKinds(Set<String> values, String name) {
        Objects.requireNonNull(values, name);
        if (values.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        TreeSet<String> copy = new TreeSet<>();
        for (String value : values) {
            copy.add(requireIdentifier(value, name + " value"));
        }
        return Collections.unmodifiableSet(copy);
    }

    private static String requireIdentifier(String value, String name) {
        String text = requireText(value, name);
        if (!text.matches("[a-z0-9][a-z0-9._-]*")) {
            throw new IllegalArgumentException(name + " must be a stable lowercase identifier");
        }
        return text;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof EngineDescriptor that)) return false;
        return id.equals(that.id) && version.equals(that.version)
                && inputKinds.equals(that.inputKinds) && outputKinds.equals(that.outputKinds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, version, inputKinds, outputKinds);
    }
}
