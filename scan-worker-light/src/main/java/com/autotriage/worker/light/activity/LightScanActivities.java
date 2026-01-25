package com.autotriage.worker.light.activity;

import com.autotriage.common.activity.ScanActivities;
import com.autotriage.common.model.ArtifactRef;
import com.autotriage.common.model.ScanRequest;
import com.autotriage.common.model.ScanState;
import com.autotriage.common.model.ScanStatus;
import com.autotriage.common.model.SuppressionApplicationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.autotriage.worker.light.security.SuppressionSignatureVerifier;
import com.autotriage.worker.light.security.TestKeySuppressionSignatureVerifier;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class LightScanActivities implements ScanActivities {

    private static final Logger log = Logger.getLogger(LightScanActivities.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Duration PROCESS_TIMEOUT = Duration.ofMinutes(2);
    private static final SuppressionSignatureVerifier signatureVerifier = new TestKeySuppressionSignatureVerifier();

    @Override
    public ArtifactRef resolveRepoSource(ScanRequest request) {
        log.infov("resolveRepoSource runId={0} repo={1} sha={2}", request.getRunId(), request.getRepository(), request.getCommitSha());
        Path workspace = null;
        try {
            workspace = Files.createTempDirectory("autotriage-source-");
            cloneRepo(request.getRepository(), request.getCommitSha(), workspace);
            Path artifactsDir = resolveArtifactsDir();
            Files.createDirectories(artifactsDir);
            Path archivePath = artifactsDir.resolve("source-" + request.getRunId() + ".tar.gz");
            createTarGzArchive(workspace, archivePath);
            return new ArtifactRef(archivePath.toUri().toString(), "source-archive");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to resolve repository source", e);
        } finally {
            if (workspace != null) {
                deleteRecursively(workspace);
            }
        }
    }

    @Override
    public ArtifactRef fetchSuppressionBundle(String repository, String ref) {
        log.infov("fetchSuppressionBundle repo={0} ref={1}", repository, ref);
        Path workspace = null;
        try {
            workspace = Files.createTempDirectory("autotriage-suppressions-");
            cloneRepo(repository, ref, workspace);
            Path suppressionsDir = workspace.resolve(".opengrep").resolve("suppressions");
            if (!Files.exists(suppressionsDir) || !Files.isDirectory(suppressionsDir)) {
                return new ArtifactRef("none://suppressions", "suppression-bundle");
            }
            Path artifactsDir = resolveArtifactsDir();
            Files.createDirectories(artifactsDir);
            String safeRef = sanitizeRef(ref);
            Path archivePath = artifactsDir.resolve("suppressions-" + safeRef + ".tar.gz");
            createTarGzArchive(suppressionsDir, archivePath);
            Files.writeString(Path.of(archivePath.toString() + ".sig"), "TEST-SIGNATURE");
            return new ArtifactRef(archivePath.toUri().toString(), "suppression-bundle");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to fetch suppression bundle", e);
        } finally {
            if (workspace != null) {
                deleteRecursively(workspace);
            }
        }
    }

    @Override
    public boolean verifySuppressionSignature(ArtifactRef bundle) {
        log.infov("verifySuppressionSignature uri={0}", bundle.getUri());
        if (bundle.getUri().startsWith("none://")) {
            return false;
        }
        Path bundlePath = resolveBundlePath(bundle.getUri());
        if (bundlePath == null) {
            return false;
        }
        return signatureVerifier.verify(bundlePath);
    }

    @Override
    public ArtifactRef runOpenGrep(ArtifactRef source, String runId) {
        throw new UnsupportedOperationException("runOpenGrep is handled by opengrep worker");
    }

    @Override
    public SuppressionApplicationResult applySuppressions(ArtifactRef rawSarif, ArtifactRef suppressionBundle) {
        throw new UnsupportedOperationException("applySuppressions is handled by filter worker");
    }

    @Override
    public void uploadResults(String runId, ArtifactRef finalSarif, ArtifactRef rawSarif, ArtifactRef suppressionReport) {
        String baseUrl = ConfigProvider.getConfig()
                .getOptionalValue("suppression.service.url", String.class)
                .orElse("http://localhost:8090");
        String endpoint = baseUrl.endsWith("/") ? baseUrl + "ingest" : baseUrl + "/ingest";
        Map<String, Object> payload = new HashMap<>();
        payload.put("runId", runId);
        payload.put("rawSarifUri", rawSarif.getUri());
        payload.put("finalSarifUri", finalSarif.getUri());
        payload.put("suppressionReportUri", suppressionReport.getUri());
        payload.put("source", "autotriage");
        try {
            String body = mapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Suppression service returned status " + response.statusCode());
            }
            JsonNode json = mapper.readTree(response.body());
            log.infov("uploadResults runId={0} reportUrl={1} dashboardUrl={2}", runId, json.path("reportUrl").asText(), json.path("dashboardUrl").asText());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to upload results", e);
        }
    }

    @Override
    public ScanStatus computeVerdict(String runId, ArtifactRef finalSarif) {
        log.infov("computeVerdict runId={0} finalUri={1}", runId, finalSarif.getUri());
        return new ScanStatus(runId, ScanState.COMPLETED, "Stub verdict: PASS");
    }

    private void cloneRepo(String repository, String commitSha, Path workspace) throws IOException, InterruptedException {
        String cloneUrl = buildCloneUrl(repository);
        runProcess(new String[] {"git", "clone", "--no-checkout", cloneUrl, workspace.toString()});
        runProcess(new String[] {"git", "-C", workspace.toString(), "checkout", commitSha});
    }

    private String buildCloneUrl(String repository) {
        Optional<String> token = ConfigProvider.getConfig().getOptionalValue("git.clone.token", String.class);
        if (token.isPresent() && repository.startsWith("https://")) {
            return repository.replace("https://", "https://x-access-token:" + token.get() + "@");
        }
        return repository;
    }

    private void runProcess(String[] command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output;
        try (InputStream stream = process.getInputStream()) {
            output = new String(stream.readAllBytes());
        }
        boolean finished = process.waitFor(PROCESS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("Command timed out: " + String.join(" ", command));
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Command failed: " + String.join(" ", command) + " output=" + output);
        }
    }

    private Path resolveArtifactsDir() {
        String dir = ConfigProvider.getConfig()
                .getOptionalValue("artifacts.dir", String.class)
                .orElse("artifacts");
        return Path.of(dir);
    }

    private void createTarGzArchive(Path sourceDir, Path outputFile) throws IOException {
        // Create a tar.gz archive while skipping the VCS metadata.
        try (OutputStream fileOut = Files.newOutputStream(outputFile);
             BufferedOutputStream buffered = new BufferedOutputStream(fileOut);
             GzipCompressorOutputStream gzipOut = new GzipCompressorOutputStream(buffered);
             TarArchiveOutputStream tarOut = new TarArchiveOutputStream(gzipOut)) {
            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path relative = sourceDir.relativize(dir);
                    if (relative.toString().startsWith(".git")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (!relative.toString().isEmpty()) {
                        TarArchiveEntry entry = new TarArchiveEntry(dir.toFile(), relative.toString() + "/");
                        tarOut.putArchiveEntry(entry);
                        tarOut.closeArchiveEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path relative = sourceDir.relativize(file);
                    if (relative.toString().startsWith(".git")) {
                        return FileVisitResult.CONTINUE;
                    }
                    TarArchiveEntry entry = new TarArchiveEntry(file.toFile(), relative.toString());
                    tarOut.putArchiveEntry(entry);
                    Files.copy(file, tarOut);
                    tarOut.closeArchiveEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private void deleteRecursively(Path root) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warnv("Failed to delete temp workspace {0}: {1}", root, e.getMessage());
        }
    }

    private Path resolveBundlePath(String uri) {
        try {
            URI bundleUri = URI.create(uri);
            if (!"file".equalsIgnoreCase(bundleUri.getScheme())) {
                return null;
            }
            return Path.of(bundleUri);
        } catch (Exception e) {
            return null;
        }
    }

    private String sanitizeRef(String ref) {
        if (ref == null || ref.isBlank()) {
            return "unknown";
        }
        return ref.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
