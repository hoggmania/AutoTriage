package com.autotriage.worker.opengrep.activity;

import com.autotriage.common.activity.ScanActivities;
import com.autotriage.common.model.ArtifactRef;
import com.autotriage.common.model.ScanRequest;
import com.autotriage.common.model.ScanStatus;
import com.autotriage.common.model.SuppressionApplicationResult;
import com.autotriage.common.model.SuppressionBundle;

import io.temporal.activity.Activity;
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
import java.util.concurrent.CompletableFuture;
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
            runOpenGrepCommand(workspace, sarifPath);
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

    private void runOpenGrepCommand(Path workspace, Path sarifPath) throws IOException, InterruptedException {
        Optional<String> binary = ConfigProvider.getConfig().getOptionalValue("opengrep.bin", String.class)
                .filter(value -> !value.isBlank());
        Optional<String> config = ConfigProvider.getConfig().getOptionalValue("opengrep.config", String.class)
                .filter(value -> !value.isBlank());
        if (binary.isEmpty() || config.isEmpty()) {
            throw new IllegalStateException("OpenGrep binary/config not configured; refusing to produce a clean stub result");
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
        CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> readProcessOutput(process));
        long deadline = System.nanoTime() + PROCESS_TIMEOUT.toNanos();
        while (process.isAlive()) {
            heartbeat("OpenGrep still running");
            if (System.nanoTime() >= deadline) {
                process.destroyForcibly();
                throw new IllegalStateException("OpenGrep timed out");
            }
            process.waitFor(10, TimeUnit.SECONDS);
        }
        String output = outputFuture.join();
        heartbeat("OpenGrep completed");
        if (process.exitValue() != 0) {
            throw new IllegalStateException("OpenGrep failed: " + output);
        }
        if (!Files.exists(sarifPath)) {
            throw new IllegalStateException("OpenGrep completed without writing SARIF output");
        }
    }

    private String readProcessOutput(Process process) {
        try (InputStream stream = process.getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "<failed to read OpenGrep output: " + e.getMessage() + ">";
        }
    }

    private void heartbeat(String detail) {
        try {
            Activity.getExecutionContext().heartbeat(detail);
        } catch (RuntimeException ignored) {
            // Unit tests may invoke this activity outside a Temporal activity context.
        }
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
