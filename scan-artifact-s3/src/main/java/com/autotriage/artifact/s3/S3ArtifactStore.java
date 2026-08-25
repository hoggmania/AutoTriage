package com.autotriage.artifact.s3;

import com.autotriage.common.artifact.ArtifactContent;
import com.autotriage.common.artifact.ArtifactStore;
import com.autotriage.common.model.ArtifactRef;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

public final class S3ArtifactStore implements ArtifactStore {
    private final S3Client client;
    private final String bucket;

    public S3ArtifactStore(S3Client client, String bucket) {
        this.client = java.util.Objects.requireNonNull(client, "client");
        if (bucket == null || bucket.isBlank()) throw new IllegalArgumentException("bucket is required");
        this.bucket = bucket;
    }

    public static S3ArtifactStore fromEnvironment() {
        String bucket = setting("artifact.s3.bucket", "ARTIFACT_S3_BUCKET")
                .orElseThrow(() -> new IllegalStateException("Object storage is required: configure artifact.s3.bucket/ARTIFACT_S3_BUCKET"));
        String region = setting("artifact.s3.region", "ARTIFACT_S3_REGION").orElse("us-east-1");
        boolean pathStyle = setting("artifact.s3.path-style", "ARTIFACT_S3_PATH_STYLE").map(Boolean::parseBoolean).orElse(false);
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build());
        setting("artifact.s3.endpoint", "ARTIFACT_S3_ENDPOINT").ifPresent(value -> builder.endpointOverride(URI.create(value)));
        return new S3ArtifactStore(builder.build(), bucket);
    }

    @Override
    public ArtifactRef put(ArtifactContent content) {
        byte[] bytes = content.bytes();
        String digest = sha256(bytes);
        String key = ArtifactRef.contentAddressedKey(digest);
        ArtifactRef ref = new ArtifactRef("s3://" + bucket + "/" + key, content.kind(), digest,
                bytes.length, content.mediaType(), key);
        if (exists(key)) {
            get(ref); // idempotent only when the immutable existing object verifies
            return ref;
        }
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket).key(key).contentLength((long) bytes.length).contentType(content.mediaType())
                .metadata(Map.of("sha256", digest, "kind", content.kind(), "run-id", content.runId(), "producer", content.producer()))
                .overrideConfiguration(c -> c.putHeader("If-None-Match", "*"))
                .build();
        try {
            client.putObject(request, RequestBody.fromBytes(bytes));
        } catch (S3Exception e) {
            if (e.statusCode() != 409 && e.statusCode() != 412) throw e;
        }
        get(ref);
        return ref;
    }

    @Override
    public ArtifactContent get(ArtifactRef ref) {
        requireDurable(ref);
        if (!ref.getObjectKey().equals(ArtifactRef.contentAddressedKey(ref.getSha256()))) {
            throw new IllegalArgumentException("Artifact object key does not match digest");
        }
        ResponseBytes<GetObjectResponse> response = client.getObjectAsBytes(b -> b.bucket(bucket).key(ref.getObjectKey()));
        byte[] bytes = response.asByteArray();
        if (bytes.length != ref.getSizeBytes() || !sha256(bytes).equals(ref.getSha256())) {
            throw new IllegalStateException("Artifact integrity verification failed for " + ref.getObjectKey());
        }
        String contentType = response.response().contentType();
        if (contentType == null || !contentType.equals(ref.getMediaType())) {
            throw new IllegalStateException("Artifact media type metadata mismatch for " + ref.getObjectKey());
        }
        Map<String, String> metadata = response.response().metadata();
        if (!ref.getSha256().equals(metadata.get("sha256")) || !ref.getKind().equals(metadata.get("kind"))) {
            throw new IllegalStateException("Artifact metadata verification failed for " + ref.getObjectKey());
        }
        return new ArtifactContent(bytes, ref.getKind(), ref.getMediaType(),
                requiredMetadata(metadata, "run-id"), requiredMetadata(metadata, "producer"));
    }

    private boolean exists(String key) {
        try {
            client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) return false;
            throw e;
        }
    }

    private static void requireDurable(ArtifactRef ref) {
        if (ref == null || !ref.isDurable() || !"s3".equalsIgnoreCase(URI.create(ref.getUri()).getScheme())) {
            throw new IllegalArgumentException("A durable S3 artifact reference is required");
        }
    }

    private static String requiredMetadata(Map<String, String> metadata, String name) {
        String value = metadata.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing artifact metadata: " + name);
        return value;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static Optional<String> setting(String property, String environment) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) value = System.getenv(environment);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    @Override
    public void close() { client.close(); }
}
