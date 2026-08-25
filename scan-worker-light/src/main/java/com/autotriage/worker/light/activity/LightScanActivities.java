package com.autotriage.worker.light.activity;

import com.autotriage.common.activity.ScanActivities;
import com.autotriage.common.artifact.ArtifactContent;
import com.autotriage.common.artifact.ArtifactStore;
import com.autotriage.artifact.s3.S3ArtifactStore;
import com.autotriage.common.model.ArtifactRef;
import com.autotriage.common.model.ScanRequest;
import com.autotriage.common.model.ScanState;
import com.autotriage.common.model.ScanStatus;
import com.autotriage.common.model.SuppressionApplicationResult;
import com.autotriage.common.model.SuppressionBundle;
import com.autotriage.common.model.SuppressionSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.autotriage.worker.light.security.ConfigurableSuppressionSignatureVerifier;
import com.autotriage.worker.light.security.SuppressionSignatureVerifier;
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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
    private final ArtifactStore artifactStore;
    private final SuppressionSignatureVerifier signatureVerifier;

    public LightScanActivities() {
        this(S3ArtifactStore.fromEnvironment());
    }

    public LightScanActivities(ArtifactStore artifactStore) {
        this(artifactStore, new ConfigurableSuppressionSignatureVerifier());
    }

    public LightScanActivities(ArtifactStore artifactStore, SuppressionSignatureVerifier signatureVerifier) {
        this.artifactStore = java.util.Objects.requireNonNull(artifactStore, "artifactStore");
        this.signatureVerifier = java.util.Objects.requireNonNull(signatureVerifier, "signatureVerifier");
    }

    @Override
    public ArtifactRef resolveRepoSource(ScanRequest request) {
        log.infov("resolveRepoSource runId={0} repo={1} sha={2}", request.getRunId(), request.getRepository(), request.getCommitSha());
        Path workspace = null;
        try {
            workspace = Files.createTempDirectory("autotriage-source-");
            cloneRepo(request.getRepository(), request.getCommitSha(), workspace);
            Path archivePath = Files.createTempFile(workspace.getParent(), "source-", ".tar.gz");
            createTarGzArchive(workspace, archivePath);
            return artifactStore.put(new ArtifactContent(Files.readAllBytes(archivePath), "source-archive",
                    "application/gzip", request.getRunId(), "light"));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to resolve repository source", e);
        } finally {
            if (workspace != null) {
                deleteRecursively(workspace);
            }
        }
    }

    @Override
    public SuppressionBundle fetchSuppressionBundle(String repository, String headRef, String baseRef) {
        log.infov("fetchSuppressionBundle repo={0} headRef={1} baseRef={2}", repository, headRef, baseRef);
        try {
            SuppressionBundle bundle = tryFetchSuppressionBundle(repository, headRef, SuppressionSource.PR_HEAD);
            if (bundle != null) {
                return bundle;
            }
            bundle = tryFetchSuppressionBundle(repository, baseRef, SuppressionSource.BASE_REF);
            if (bundle != null) {
                return bundle;
            }
            return SuppressionBundle.none();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to fetch suppression bundle", e);
        }
    }

    @Override
    public boolean verifySuppressionSignature(ArtifactRef bundle) {
        log.infov("verifySuppressionSignature uri={0}", bundle.getUri());
        if (bundle.getUri().startsWith("none://")) {
            return false;
        }
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("autotriage-signature-");
            Path bundlePath = artifactStore.materialize(bundle, tempDir, "suppressions.tar.gz");
            return signatureVerifier.verify(bundlePath);
        } catch (Exception e) {
            return false;
        } finally {
            if (tempDir != null) deleteRecursively(tempDir);
        }
    }

    @Override
    public ArtifactRef runOpenGrep(ArtifactRef source, String runId) {
        throw new UnsupportedOperationException("runOpenGrep is handled by opengrep worker");
    }

    @Override
    public SuppressionApplicationResult applySuppressions(ArtifactRef rawSarif,
                                                          ArtifactRef suppressionBundle,
                                                          ArtifactRef sourceArchive,
                                                          ScanRequest request) {
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
        try {
            Path tempDir = Files.createTempDirectory("autotriage-verdict-");
            Path sarifPath = artifactStore.materialize(finalSarif, tempDir, "final.sarif");
            JsonNode sarif = mapper.readTree(Files.readString(sarifPath, StandardCharsets.UTF_8));
            JsonNode results = sarif.at("/runs/0/results");
            int high = 0;
            int medium = 0;
            int low = 0;
            if (results != null && results.isArray()) {
                for (JsonNode result : results) {
                    Severity severity = resolveSeverity(result);
                    switch (severity) {
                        case HIGH -> high++;
                        case MEDIUM -> medium++;
                        case LOW -> low++;
                    }
                }
            }
            GatePolicy policy = GatePolicy.fromConfig();
            Verdict verdict = policy.evaluate(high, medium, low);
            String message = String.format(
                    "Verdict: %s (high=%d, medium=%d, low=%d; %s)",
                    verdict.label(),
                    high,
                    medium,
                    low,
                    policy.describe());
            ScanStatus status = new ScanStatus(runId, ScanState.COMPLETED, message);
            deleteRecursively(tempDir);
            return status;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute verdict", e);
        }
    }

    private void cloneRepo(String repository, String commitSha, Path workspace) throws IOException, InterruptedException {
        String cloneUrl = buildCloneUrl(repository);
        runProcess(new String[] {"git", "clone", "--no-checkout", cloneUrl, workspace.toString()});
        runProcess(new String[] {"git", "-C", workspace.toString(), "checkout", commitSha});
    }

    private String buildCloneUrl(String repository) {
        Optional<String> token = ConfigProvider.getConfig().getOptionalValue("git.clone.token", String.class);
        if (token.isPresent() && repository.startsWith("https://")) {
            String encodedToken = URLEncoder.encode(token.get(), StandardCharsets.UTF_8)
                    .replace("+", "%20");
            return repository.replaceFirst("^https://", "https://x-access-token:" + encodedToken + "@");
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
            throw new IllegalStateException("Command timed out: " + redactCommand(command));
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Command failed: " + redactCommand(command) + " output=" + redactSecrets(output));
        }
    }

    private String redactCommand(String[] command) {
        String joined = String.join(" ", command);
        return redactSecrets(joined);
    }

    private String redactSecrets(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("https://[^:@\\s]+:[^@\\s]+@", "https://***:***@");
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


    private String sanitizeRef(String ref) {
        if (ref == null || ref.isBlank()) {
            return "unknown";
        }
        return ref.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private SuppressionBundle tryFetchSuppressionBundle(String repository, String ref, SuppressionSource source) throws IOException, InterruptedException {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        Path workspace = null;
        try {
            workspace = Files.createTempDirectory("autotriage-suppressions-");
            cloneRepo(repository, ref, workspace);
            Path suppressionsDir = workspace.resolve(".opengrep").resolve("suppressions");
            if (!Files.exists(suppressionsDir) || !Files.isDirectory(suppressionsDir)) {
                return null;
            }
            Path archivePath = Files.createTempFile(workspace.getParent(), "suppressions-", ".tar.gz");
            createTarGzArchive(suppressionsDir, archivePath);
            ArtifactRef stored = artifactStore.put(new ArtifactContent(Files.readAllBytes(archivePath),
                    "suppression-bundle", "application/gzip", sanitizeRef(ref), "light"));
            return new SuppressionBundle(stored, source);
        } finally {
            if (workspace != null) {
                deleteRecursively(workspace);
            }
        }
    }

    private Severity resolveSeverity(JsonNode result) {
        JsonNode severityNode = result.at("/properties/severity");
        if (severityNode != null && severityNode.isTextual()) {
            return mapSeverityValue(severityNode.asText());
        }
        JsonNode levelNode = result.get("level");
        if (levelNode != null && levelNode.isTextual()) {
            return mapSarifLevel(levelNode.asText());
        }
        return Severity.LOW;
    }

    private Severity mapSeverityValue(String value) {
        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case "CRITICAL", "HIGH" -> Severity.HIGH;
            case "MEDIUM", "MODERATE" -> Severity.MEDIUM;
            case "LOW", "INFO" -> Severity.LOW;
            default -> Severity.LOW;
        };
    }

    private Severity mapSarifLevel(String level) {
        String normalized = level.trim().toLowerCase();
        return switch (normalized) {
            case "error" -> Severity.HIGH;
            case "warning" -> Severity.MEDIUM;
            case "note", "none" -> Severity.LOW;
            default -> Severity.LOW;
        };
    }

    private enum Severity {
        HIGH,
        MEDIUM,
        LOW
    }

    private record GatePolicy(boolean failOnAny, int maxHigh, int maxMedium, int maxLow) {
        private static final int UNLIMITED = Integer.MAX_VALUE;

        static GatePolicy fromConfig() {
            var config = ConfigProvider.getConfig();
            boolean failOnAny = config.getOptionalValue("gate.policy.fail-on-any", Boolean.class)
                    .orElse(false);
            int maxHigh = normalizeLimit(config.getOptionalValue("gate.policy.max-high", Integer.class), 0);
            int maxMedium = normalizeLimit(config.getOptionalValue("gate.policy.max-medium", Integer.class), UNLIMITED);
            int maxLow = normalizeLimit(config.getOptionalValue("gate.policy.max-low", Integer.class), UNLIMITED);
            return new GatePolicy(failOnAny, maxHigh, maxMedium, maxLow);
        }

        Verdict evaluate(int high, int medium, int low) {
            int total = high + medium + low;
            if (failOnAny && total > 0) {
                return Verdict.FAIL;
            }
            if (high > maxHigh || medium > maxMedium || low > maxLow) {
                return Verdict.FAIL;
            }
            return Verdict.PASS;
        }

        String describe() {
            return String.format(
                    "failOnAny=%s, thresholds: high<=%s, medium<=%s, low<=%s",
                    failOnAny,
                    formatLimit(maxHigh),
                    formatLimit(maxMedium),
                    formatLimit(maxLow));
        }

        private static int normalizeLimit(Optional<Integer> value, int defaultValue) {
            if (value.isEmpty()) {
                return defaultValue;
            }
            int limit = value.get();
            return limit < 0 ? UNLIMITED : limit;
        }

        private static String formatLimit(int limit) {
            return limit == UNLIMITED ? "unlimited" : Integer.toString(limit);
        }
    }

    private enum Verdict {
        PASS,
        FAIL;

        String label() {
            return name();
        }
    }
}
