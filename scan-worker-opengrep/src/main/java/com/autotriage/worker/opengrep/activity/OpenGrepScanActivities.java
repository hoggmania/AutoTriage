package com.autotriage.worker.opengrep.activity;

import com.autotriage.common.activity.ScanActivities;
import com.autotriage.common.artifact.ArtifactContent;
import com.autotriage.common.artifact.ArtifactStore;
import com.autotriage.artifact.s3.S3ArtifactStore;
import com.autotriage.common.engine.AnalysisEngine;
import com.autotriage.common.engine.EngineDescriptor;
import com.autotriage.common.engine.EngineRequest;
import com.autotriage.common.engine.EngineResult;
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
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class OpenGrepScanActivities implements ScanActivities, AnalysisEngine {

    private static final Logger log = Logger.getLogger(OpenGrepScanActivities.class);
    private static final Duration PROCESS_TIMEOUT = Duration.ofMinutes(30);
    private static final int MAX_ARCHIVE_ENTRIES = 100_000;
    private static final long MAX_ENTRY_BYTES = 100L * 1024 * 1024;
    private static final long MAX_EXPANDED_BYTES = 2L * 1024 * 1024 * 1024;
    private static final EngineDescriptor DESCRIPTOR = new EngineDescriptor(
            "opengrep", "1", Set.of("source-archive"), Set.of("sarif-raw"));
    private final ArtifactStore artifactStore;

    public OpenGrepScanActivities() { this(S3ArtifactStore.fromEnvironment()); }

    public OpenGrepScanActivities(ArtifactStore artifactStore) {
        this.artifactStore = java.util.Objects.requireNonNull(artifactStore, "artifactStore");
    }

    @Override
    public EngineDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public EngineResult analyze(EngineRequest request) {
        ArtifactRef output = runOpenGrep(request.getSource(), request.getRunId());
        return new EngineResult(DESCRIPTOR.getId(), DESCRIPTOR.getVersion(), List.of(output), List.of());
    }

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
            Path sourceArchive = artifactStore.materialize(source, workspace, "source.tar.gz");
            extractTarGz(sourceArchive, workspace);
            Files.delete(sourceArchive);
            Path outputDirectory = Files.createDirectory(workspace.resolve(".autotriage-output"));
            Path sarifPath = outputDirectory.resolve("raw.sarif");
            runOpenGrepCommand(workspace, outputDirectory, sarifPath);
            return artifactStore.put(new ArtifactContent(Files.readAllBytes(sarifPath), "sarif-raw",
                    "application/sarif+json", runId, "opengrep"));
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


    private void runOpenGrepCommand(Path workspace, Path outputDirectory, Path sarifPath)
            throws IOException, InterruptedException {
        Optional<String> binary = ConfigProvider.getConfig().getOptionalValue("opengrep.bin", String.class)
                .filter(value -> !value.isBlank());
        Optional<String> config = ConfigProvider.getConfig().getOptionalValue("opengrep.config", String.class)
                .filter(value -> !value.isBlank());
        if (binary.isEmpty() || config.isEmpty()) {
            throw new IllegalStateException("OpenGrep binary/config not configured; refusing to produce a clean stub result");
        }
        String sandbox = ConfigProvider.getConfig()
                .getOptionalValue("sandbox.bin", String.class).filter(value -> !value.isBlank())
                .orElse("/usr/bin/bwrap");
        Path sandboxPath = Path.of(sandbox);
        if (!sandboxPath.isAbsolute() || !Files.isExecutable(sandboxPath)) {
            throw new IllegalStateException("Sandbox executable is unavailable; refusing unsandboxed analysis");
        }
        Path enginePath = Path.of(binary.get());
        Path configPath = Path.of(config.get());
        if (!enginePath.isAbsolute() || !configPath.isAbsolute()) {
            throw new IllegalStateException("OpenGrep binary and config must be absolute paths");
        }
        List<String> command = new ArrayList<>(List.of(
                sandbox,
                "--unshare-all",
                "--die-with-parent",
                "--new-session",
                "--clearenv",
                "--proc", "/proc",
                "--dev", "/dev",
                "--tmpfs", "/tmp"));
        for (String systemPath : List.of("/usr", "/bin", "/lib", "/lib64")) {
            if (Files.exists(Path.of(systemPath))) {
                command.addAll(List.of("--ro-bind", systemPath, systemPath));
            }
        }
        command.addAll(List.of(
                "--ro-bind", enginePath.toString(), "/autotriage-engine",
                "--ro-bind", configPath.toString(), "/autotriage-rules",
                "--ro-bind", workspace.toString(), "/workspace",
                "--bind", outputDirectory.toString(), "/output",
                "--setenv", "HOME", "/tmp",
                "--setenv", "PATH", "/usr/local/bin:/usr/bin:/bin",
                "--chdir", "/workspace",
                "/autotriage-engine",
                "--config", "/autotriage-rules",
                "--sarif",
                "--output", "/output/raw.sarif",
                "/workspace"));
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
        } catch (IllegalStateException ignored) {
            // Unit tests may invoke this activity outside a Temporal activity context.
        }
    }

    private void extractTarGz(Path archive, Path destDir) throws IOException {
        try (InputStream fileIn = Files.newInputStream(archive);
             BufferedInputStream buffered = new BufferedInputStream(fileIn);
             GzipCompressorInputStream gzipIn = new GzipCompressorInputStream(buffered);
             TarArchiveInputStream tarIn = new TarArchiveInputStream(gzipIn)) {
            TarArchiveEntry entry;
            int entries = 0;
            long expandedBytes = 0;
            while ((entry = tarIn.getNextEntry()) != null) {
                if (++entries > MAX_ARCHIVE_ENTRIES) {
                    throw new IOException("Source archive has too many entries");
                }
                if (entry.isSymbolicLink() || entry.isLink() || entry.isCharacterDevice()
                        || entry.isBlockDevice() || entry.isFIFO()) {
                    throw new IOException("Source archive contains unsupported link or device entry");
                }
                if (entry.getSize() > MAX_ENTRY_BYTES || entry.getSize() < 0) {
                    throw new IOException("Source archive entry exceeds size limit");
                }
                expandedBytes = Math.addExact(expandedBytes, entry.getSize());
                if (expandedBytes > MAX_EXPANDED_BYTES) {
                    throw new IOException("Source archive exceeds expanded size limit");
                }
                Path target = destDir.resolve(entry.getName()).normalize();
                if (!target.startsWith(destDir)) {
                    throw new IOException("Source archive entry escapes extraction root");
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
