package com.autotriage.test;

import com.autotriage.common.artifact.ArtifactContent;
import com.autotriage.common.artifact.ArtifactStore;
import com.autotriage.common.model.ArtifactRef;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TestArtifactStore implements ArtifactStore {
    private final Map<String, ArtifactContent> objects = new ConcurrentHashMap<>();

    @Override
    public ArtifactRef put(ArtifactContent content) {
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.bytes()));
            String key = ArtifactRef.contentAddressedKey(digest);
            objects.putIfAbsent(key, content);
            return new ArtifactRef("artifact://store/" + key, content.kind(), digest, content.sizeBytes(), content.mediaType(), key);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public ArtifactContent get(ArtifactRef ref) {
        if (ref == null || !ref.isDurable()) throw new IllegalArgumentException("durable artifact required");
        ArtifactContent content = objects.get(ref.getObjectKey());
        if (content == null) throw new IllegalArgumentException("artifact not found");
        return content;
    }
}
