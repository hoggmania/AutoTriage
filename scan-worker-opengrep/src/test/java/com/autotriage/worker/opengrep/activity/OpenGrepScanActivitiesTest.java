package com.autotriage.worker.opengrep.activity;

import com.autotriage.common.engine.AnalysisEngine;
import com.autotriage.common.engine.EngineDescriptor;
import com.autotriage.common.model.ArtifactRef;
import com.autotriage.test.TestArtifactStore;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenGrepScanActivitiesTest {

    private Path tempRoot;

    @AfterEach
    void cleanup() throws Exception {
        System.clearProperty("opengrep.bin");
        System.clearProperty("opengrep.config");
        if (tempRoot != null) {
            Files.walk(tempRoot)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    @Test
    void exposesOpenGrepThroughStableAnalysisEngineDescriptor() {
        OpenGrepScanActivities activities = new OpenGrepScanActivities(new TestArtifactStore());

        AnalysisEngine engine = assertInstanceOf(AnalysisEngine.class, activities);
        EngineDescriptor descriptor = engine.descriptor();

        assertEquals("opengrep", descriptor.getId());
        assertEquals("1", descriptor.getVersion());
        assertEquals(java.util.Set.of("source-archive"), descriptor.getInputKinds());
        assertEquals(java.util.Set.of("sarif-raw"), descriptor.getOutputKinds());
    }

    @Test
    void runOpenGrepFailsClosedWhenBinaryOrConfigMissing() throws Exception {
        tempRoot = Files.createTempDirectory("opengrep-activities-test-");
        Path sourceArchive = tempRoot.resolve("source.tar.gz");
        writeSourceArchive(sourceArchive);
        TestArtifactStore artifactStore = new TestArtifactStore();
        ArtifactRef source = artifactStore.putBytes(
                Files.readAllBytes(sourceArchive), "source-archive", "application/gzip");

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                new OpenGrepScanActivities(artifactStore).runOpenGrep(source, "run-missing-config"));

        assertTrue(error.getMessage().contains("Failed to run OpenGrep"));
        assertTrue(error.getCause().getMessage().contains("OpenGrep binary/config not configured"));
    }

    private static void writeSourceArchive(Path archivePath) throws Exception {
        byte[] bytes = "class Example {}\n".getBytes(StandardCharsets.UTF_8);
        try (OutputStream fileOut = Files.newOutputStream(archivePath);
             BufferedOutputStream buffered = new BufferedOutputStream(fileOut);
             GzipCompressorOutputStream gzipOut = new GzipCompressorOutputStream(buffered);
             TarArchiveOutputStream tarOut = new TarArchiveOutputStream(gzipOut)) {
            TarArchiveEntry entry = new TarArchiveEntry("Example.java");
            entry.setSize(bytes.length);
            tarOut.putArchiveEntry(entry);
            tarOut.write(bytes);
            tarOut.closeArchiveEntry();
        }
    }
}
