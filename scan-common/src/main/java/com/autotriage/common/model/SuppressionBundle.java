package com.autotriage.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public final class SuppressionBundle {
    private final ArtifactRef bundle;
    private final SuppressionSource source;

    @JsonCreator
    public SuppressionBundle(
            @JsonProperty("bundle") ArtifactRef bundle,
            @JsonProperty("source") SuppressionSource source) {
        this.bundle = Objects.requireNonNull(bundle, "bundle");
        this.source = Objects.requireNonNull(source, "source");
    }

    public ArtifactRef getBundle() {
        return bundle;
    }

    public SuppressionSource getSource() {
        return source;
    }

    public static SuppressionBundle none() {
        return new SuppressionBundle(
                new ArtifactRef("none://suppressions", "suppression-bundle"),
                SuppressionSource.NONE);
    }
}
