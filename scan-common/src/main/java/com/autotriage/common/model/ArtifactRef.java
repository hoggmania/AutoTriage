package com.autotriage.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class ArtifactRef {
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private final String uri;
    private final String kind;
    private final String sha256;
    private final long sizeBytes;
    private final String mediaType;
    private final String objectKey;

    /** Legacy constructor retained for Temporal history/test compatibility. Workers reject it for durable I/O. */
    public ArtifactRef(String uri, String kind) {
        this.uri = requireText(uri, "uri");
        this.kind = requireText(kind, "kind");
        this.sha256 = null;
        this.sizeBytes = -1;
        this.mediaType = null;
        this.objectKey = null;
    }

    @JsonCreator
    public ArtifactRef(@JsonProperty("uri") String uri,
                       @JsonProperty("kind") String kind,
                       @JsonProperty("sha256") String sha256,
                       @JsonProperty("sizeBytes") long sizeBytes,
                       @JsonProperty("mediaType") String mediaType,
                       @JsonProperty("objectKey") String objectKey) {
        this.uri = requireText(uri, "uri");
        this.kind = requireText(kind, "kind");
        // Temporal must still be able to replay histories written before durable artifact metadata existed.
        // Production ArtifactStore implementations reject these non-durable references at every I/O boundary.
        if (sha256 == null) {
            this.sha256 = null;
            this.sizeBytes = -1;
            this.mediaType = null;
            this.objectKey = null;
            return;
        }
        this.sha256 = requireDigest(sha256);
        if (sizeBytes < 0) throw new IllegalArgumentException("sizeBytes must be non-negative");
        this.sizeBytes = sizeBytes;
        this.mediaType = requireText(mediaType, "mediaType");
        this.objectKey = requireText(objectKey, "objectKey");
        if (!objectKey.equals(contentAddressedKey(this.sha256))) {
            throw new IllegalArgumentException("objectKey must be content-addressed by sha256");
        }
        URI parsed = URI.create(this.uri);
        if (parsed.getScheme() == null || "file".equalsIgnoreCase(parsed.getScheme())) {
            throw new IllegalArgumentException("Durable artifact URI must not use file scheme");
        }
        if (!parsed.getPath().endsWith("/" + objectKey) && !parsed.getScheme().equalsIgnoreCase("artifact")) {
            throw new IllegalArgumentException("uri must identify objectKey");
        }
    }

    public static String contentAddressedKey(String digest) {
        String normalized = requireDigest(digest);
        return "sha256/" + normalized.substring(0, 2) + "/" + normalized;
    }

    @JsonIgnore
    public boolean isDurable() { return sha256 != null; }
    public String getUri() { return uri; }
    public String getKind() { return kind; }
    public String getSha256() { return sha256; }
    public long getSizeBytes() { return sizeBytes; }
    public String getMediaType() { return mediaType; }
    public String getObjectKey() { return objectKey; }

    private static String requireDigest(String value) {
        String normalized = requireText(value, "sha256").toLowerCase(Locale.ROOT);
        if (!SHA256.matcher(normalized).matches()) throw new IllegalArgumentException("sha256 must be 64 lowercase hex characters");
        return normalized;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
