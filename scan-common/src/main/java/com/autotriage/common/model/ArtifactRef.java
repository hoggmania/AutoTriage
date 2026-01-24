package com.autotriage.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public final class ArtifactRef {
    private final String uri;
    private final String kind;

    @JsonCreator
    public ArtifactRef(@JsonProperty("uri") String uri, @JsonProperty("kind") String kind) {
        this.uri = Objects.requireNonNull(uri, "uri");
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public String getUri() {
        return uri;
    }

    public String getKind() {
        return kind;
    }
}
