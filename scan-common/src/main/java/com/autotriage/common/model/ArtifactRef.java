package com.autotriage.common.model;

import java.util.Objects;

public final class ArtifactRef {
    private final String uri;
    private final String kind;

    public ArtifactRef(String uri, String kind) {
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
