package com.autotriage.common.artifact;

import com.autotriage.common.model.ArtifactRef;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public interface ArtifactStore extends AutoCloseable {
    ArtifactRef put(ArtifactContent content);

    ArtifactContent get(ArtifactRef ref);

    default Path materialize(ArtifactRef ref, Path privateDirectory, String fileName) throws IOException {
        if (Path.of(fileName).isAbsolute() || Path.of(fileName).getNameCount() != 1) {
            throw new IllegalArgumentException("fileName must be a single relative name");
        }
        ArtifactContent content = get(ref);
        Files.createDirectories(privateDirectory);
        Path target = privateDirectory.resolve(fileName).normalize();
        if (!target.getParent().equals(privateDirectory.normalize())) {
            throw new IllegalArgumentException("Materialized path escapes private directory");
        }
        Files.write(target, content.bytes());
        return target;
    }

    @Override
    default void close() { }
}
