package com.autotriage.worker.opengrep.activity;

import com.autotriage.common.activity.ScanActivities;
import com.autotriage.common.model.ArtifactRef;
import com.autotriage.common.model.ScanRequest;
import com.autotriage.common.model.ScanStatus;
import com.autotriage.common.model.SuppressionApplicationResult;
import com.autotriage.common.model.SuppressionBundle;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class OpenGrepScanActivities implements ScanActivities {

    private static final Logger log = Logger.getLogger(OpenGrepScanActivities.class);
    private static final Duration PROCESS_TIMEOUT = Duration.ofMinutes(30);

    @Override
    public ArtifactRef resolveRepoSource(ScanRequest request) {
        throw new UnsupportedOperationException("resolveRepoSource is handled by light worker");
    }

    @Override
    public SuppressionBundle fetchSuppressionBundle(String repository, String headRef, String baseRef) {
        throw new UnsupportedOperationException("fetchSuppressionBundle is handled by light worker");
    }

    @Override
    public boolean verifySuppressionSignature(ArtifactRef bundle) {
        throw new UnsupportedOperationException("verifySuppressionSignature is handled by light worker");
    }

    @Override
    public ArtifactRef runOpenGrep(ArtifactRef source, String runId) {
        log.infov("runOpenGrep runId={0} sourceUri={1}", runId, source.getUri());
        Path workspace = null;
        try {
            workspace = Files.createTempDirectory("autotriage-opengrep-");
            Path sourceArchive = resolveSourceArchive(source.getUri());
            extractTarGz(sourceArchive, workspace);
            Path artifactsDir = resolveArtifactsDir();
            Files.createDirectories(artifactsDir);
            Path sarifPath = artifactsDir.resolve("raw-" + runId + ".sarif");
            boolean ran = runOpenGrepCommand(workspace, sarifPath);
            if (!ran) {
                writeStubSarif(sarifPath, runId);
            }
            return new ArtifactRef(sarifPath.toUri().toString(), "sarif-raw");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to run OpenGrep", e);
        } finally {
            if (workspace != null) {
                deleteRecursively(workspace);
            }
        }
    }

    @Override
    public SuppressionApplicationResult applySuppressions(ArtifactRef rawSarif,
                                                          ArtifactRef suppressionBundle,
                                                          ArtifactRef sourceArchive,
                                                          ScanRequest request) {
        throw new UnsupportedOperationException("applySuppressions is handled by filter worker");
    }

    @Override
    public  void uploadResults(String runId, ArtifactRef finalSarif, ArtifactRef rawSarif, ArtifactRef suppressionReport) {
        throw new UnsupportedOperationException("uploadResults is handled by light worker");
    }

    @Override
    public ScanStatus computeVerdict(String runId, ArtifactRef finalSarif) {
        throw new UnsupportedOperationException("computeVerdict is handled by light worker");
    }

    private Path resolveSourceArchive(String uri) {
        URI sourceUri = URI.create(uri);
        if (!"file".equalsIgnoreCase(sourceUri.getScheme())) {
            throw new IllegalArgumentException("Only file:// source archives are supported in this phase");
        }
        return Path.of(sourceUri);
    }

    private Path resolveArtifactsDir() {
        String dir = ConfigProvider.getConfig()
                .getOptionalValue("artifacts.dir", String.class)
                .orElse("artifacts");
        return Path.of(dir);
    }

    private boolean runOpenGrepCommand(Path workspace, Path sarifPath) throws IOException, InterruptedException {
        Optional<String> binary = ConfigProvider.getConfig().getOptionalValue("opengrep.bin", String.class);
        Optional<String> config = ConfigProvider.getConfig().getOptionalValue("opengrep.config", String.class);
        if (binary.isEmpty() || config.isEmpty()) {
            log.warn("OpenGrep binary/config not configured, writing stub SARIF instead");
            return false;
        }
        String[] command = new String[] {
                binary.get(),
                "--config", config.get(),
                "--sarif",
                "--output", sarifPath.toString(),
                workspace.toString()
        };
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output;
        try (InputStream stream = process.getInputStream()) {
            output = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        boolean finished = process.waitFor(PROCESS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("OpenGrep timed out");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("OpenGrep failed: " + output);
        }
        return true;
    }

    private void writeStubSarif(Path sarifPath, String runId) throws IOException {
        String sarif = "{\n" +
                "  \"version\": \"2.1.0\",\n" +
                "  \"runs\": [\n" +
                "    {\n" +
                "      \"tool\": {\"driver\": {\"name\": \"OpenGrep\", \"version\": \"stub\"}},\n" +
                "      \"results\": []\n" +
                "    }\n" +
                "  ],\n" +
                "  \"properties\": {\"runId\": \"" + runId + "\"}\n" +
                "}\n";
        Files.writeString(sarifPath, sarif, StandardCharsets.UTF_8);
    }

    private void extractTarGz(Path archive, Path destDir) throws IOException {
        try (InputStream fileIn = Files.newInputStream(archive);
             BufferedInputStream buffered = new BufferedInputStream(fileIn);
             GzipCompressorInputStream gzipIn = new GzipCompressorInputStream(buffered);
             TarArchiveInputStream tarIn = new TarArchiveInputStream(gzipIn)) {
            TarArchiveEntry entry;
            while ((entry = tarIn.getNextEntry()) != null) {
                Path target = destDir.resolve(entry.getName()).normalize();
                if (!target.startsWith(destDir)) {
                    continue;
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(target))) {
                        tarIn.transferTo(out);
                    }
                }
            }
        }
    }

    private void deleteRecursively(Path root) {
        try {
            Files.walk(root)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            log.warnv("Failed to delete {0}: {1}", path, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warnv("Failed to delete workspace {0}: {1}", root, e.getMessage());
        }
    }
}
