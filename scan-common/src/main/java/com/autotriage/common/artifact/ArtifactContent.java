package com.autotriage.common.artifact;

import java.util.Arrays;
import java.util.Objects;

public final class ArtifactContent {
    private final byte[] bytes;
    private final String kind;
    private final String mediaType;
    private final String runId;
    private final String producer;

    public ArtifactContent(byte[] bytes, String kind, String mediaType, String runId, String producer) {
        this.bytes = Arrays.copyOf(Objects.requireNonNull(bytes, "bytes"), bytes.length);
        this.kind = requireText(kind, "kind");
        this.mediaType = requireText(mediaType, "mediaType");
        this.runId = requireText(runId, "runId");
        this.producer = requireText(producer, "producer");
    }

    public byte[] bytes() { return Arrays.copyOf(bytes, bytes.length); }
    public long sizeBytes() { return bytes.length; }
    public String kind() { return kind; }
    public String mediaType() { return mediaType; }
    public String runId() { return runId; }
    public String producer() { return producer; }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
