package com.autotriage.worker.filter.activity;

import com.autotriage.common.activity.ScanActivities;
import com.autotriage.common.model.ArtifactRef;
import com.autotriage.common.model.ScanRequest;
import com.autotriage.common.model.ScanStatus;
import com.autotriage.common.model.SuppressionApplicationResult;
import com.autotriage.common.model.SuppressionBundle;
import com.autotriage.common.model.TriageCandidateRequest;
import com.autotriage.common.model.TriageCandidateResponse;
import com.autotriage.common.model.TriageClassification;
import com.autotriage.worker.filter.model.SuppressionReport;
import com.autotriage.worker.filter.triage.TriageClient;
import com.autotriage.worker.filter.zerofalse.ZeroFalseContext;
import com.autotriage.worker.filter.zerofalse.ZeroFalseContextBuilder;
import com.autotriage.worker.filter.zerofalse.ZeroFalseCweResolver;
import com.autotriage.worker.filter.zerofalse.ZeroFalseEvaluator;
import com.autotriage.worker.filter.zerofalse.ZeroFalseEvaluatorDisabled;
import com.autotriage.worker.filter.zerofalse.ZeroFalsePromptLibrary;
import com.autotriage.worker.filter.zerofalse.ZeroFalseSettings;
import com.autotriage.worker.filter.zerofalse.ZeroFalseVerdict;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class FilterScanActivities implements ScanActivities {

    private static final Logger log = Logger.getLogger(FilterScanActivities.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final ZeroFalsePromptLibrary promptLibrary;
    private final ZeroFalseContextBuilder contextBuilder;
    private final ZeroFalseEvaluator zeroFalseEvaluator;
    private final TriageClient triageClient;

    public FilterScanActivities() {
        this(new ZeroFalsePromptLibrary(), new ZeroFalseContextBuilder(), new ZeroFalseEvaluatorDisabled(), new TriageClient());
    }

    @Inject
    public FilterScanActivities(ZeroFalsePromptLibrary promptLibrary,
                                ZeroFalseContextBuilder contextBuilder,
                                ZeroFalseEvaluator zeroFalseEvaluator,
                                TriageClient triageClient) {
        this.promptLibrary = promptLibrary;
        this.contextBuilder = contextBuilder;
        this.zeroFalseEvaluator = zeroFalseEvaluator;
        this.triageClient = triageClient;
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
        throw new UnsupportedOperationException("runOpenGrep is handled by opengrep worker");
    }

    @Override
    public SuppressionApplicationResult applySuppressions(ArtifactRef rawSarif,
                                                          ArtifactRef suppressionBundle,
                                                          ArtifactRef sourceArchive,
                                                          ScanRequest request) {
        String sourceUri = sourceArchive == null ? "null" : sourceArchive.getUri();
        log.infov("applySuppressions rawUri={0} suppressionUri={1} sourceUri={2}", rawSarif.getUri(), suppressionBundle.getUri(), sourceUri);
        Path sourceRoot = null;
        try {
            Path rawPath = resolveFilePath(rawSarif.getUri());
            JsonNode sarif = mapper.readTree(Files.readString(rawPath, StandardCharsets.UTF_8));
            JsonNode runNode = sarif.at("/runs/0");
            JsonNode resultsNode = sarif.at("/runs/0/results");
            ArrayNode results = resultsNode != null && resultsNode.isArray()
                    ? (ArrayNode) resultsNode
                    : mapper.createArrayNode();
            Map<String, JsonNode> suppressions = loadSuppressions(suppressionBundle);
            ArrayNode filtered = mapper.createArrayNode();
            int suppressed = 0;
            int expired = 0;
            int invalid = 0;
            for (JsonNode result : results) {
                String fingerprint = extractFingerprint(result);
                if (fingerprint == null) {
                    filtered.add(result);
                    continue;
                }
                JsonNode suppression = suppressions.get(fingerprint);
                if (suppression == null) {
                    filtered.add(result);
                    continue;
                }
                SuppressionDecision decision = evaluateSuppression(suppression);
                switch (decision) {
                    case APPLY -> suppressed++;
                    case EXPIRED -> expired++;
                    case INVALID -> {
                        invalid++;
                        filtered.add(result);
                    }
                    case NONE -> filtered.add(result);
                }
            }

            ZeroFalseSettings settings = ZeroFalseSettings.fromConfig();
            int llmSuppressed = 0;
            if (settings.enabled()) {
                sourceRoot = extractSourceArchive(sourceArchive);
                if (sourceRoot == null) {
                    log.warn("ZeroFalse enabled but source archive unavailable; skipping LLM suppressions");
                } else {
                    ZeroFalseResult zeroFalseResult = applyZeroFalseFiltering(filtered, runNode, sourceRoot, settings, request);
                    filtered = zeroFalseResult.filtered();
                    llmSuppressed = zeroFalseResult.suppressed();
                }
            }

            if (runNode != null && runNode.isObject()) {
                ((ObjectNode) runNode).set("results", filtered);
            }

            Path artifactsDir = resolveArtifactsDir();
            Files.createDirectories(artifactsDir);
            Path finalSarifPath = artifactsDir.resolve("final-" + rawPath.getFileName().toString());
            Files.writeString(finalSarifPath, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(sarif), StandardCharsets.UTF_8);
            SuppressionReport report = new SuppressionReport(suppressed, expired, invalid, llmSuppressed);
            Path reportPath = artifactsDir.resolve("suppression-report-" + rawPath.getFileName().toString().replace(".sarif", ".json"));
            Files.writeString(reportPath, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report), StandardCharsets.UTF_8);
            return new SuppressionApplicationResult(
                    new ArtifactRef(finalSarifPath.toUri().toString(), "sarif-final"),
                    new ArtifactRef(reportPath.toUri().toString(), "suppression-report"));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to apply suppressions", e);
        } finally {
            if (sourceRoot != null) {
                deleteRecursively(sourceRoot);
            }
        }
    }

    @Override
    public void uploadResults(String runId, ArtifactRef finalSarif, ArtifactRef rawSarif, ArtifactRef suppressionReport) {
        throw new UnsupportedOperationException("uploadResults is handled by light worker");
    }

    @Override
    public ScanStatus computeVerdict(String runId, ArtifactRef finalSarif) {
        throw new UnsupportedOperationException("computeVerdict is handled by light worker");
    }

    private Path resolveFilePath(String uri) {
        URI sourceUri = URI.create(uri);
        if (!"file".equalsIgnoreCase(sourceUri.getScheme())) {
            throw new IllegalArgumentException("Only file:// SARIF is supported in this phase");
        }
        return Path.of(sourceUri);
    }

    private Path resolveArtifactsDir() {
        String dir = ConfigProvider.getConfig()
                .getOptionalValue("artifacts.dir", String.class)
                .orElse("artifacts");
        return Path.of(dir);
    }

    private Map<String, JsonNode> loadSuppressions(ArtifactRef suppressionBundle) throws IOException {
        Map<String, JsonNode> suppressions = new HashMap<>();
        if (suppressionBundle.getUri().startsWith("none://")) {
            return suppressions;
        }
        Path bundlePath = resolveFilePath(suppressionBundle.getUri());
        Path tempDir = Files.createTempDirectory("autotriage-suppressions-filter-");
        try {
            extractTarGz(bundlePath, tempDir);
            Files.walk(tempDir)
                    .filter(path -> path.toString().endsWith(".yml") || path.toString().endsWith(".yaml"))
                    .forEach(path -> {
                        try {
                            JsonNode root = mapper.readTree(path.toFile());
                            if (root.isArray()) {
                                for (JsonNode entry : root) {
                                    String fp = entry.path("fingerprint").asText(null);
                                    if (fp != null) {
                                        suppressions.put(fp, entry);
                                    }
                                }
                            } else if (root.isObject()) {
                                String fp = root.path("fingerprint").asText(null);
                                if (fp != null) {
                                    suppressions.put(fp, root);
                                }
                            }
                        } catch (IOException e) {
                            log.warnv("Failed to parse suppression file {0}: {1}", path, e.getMessage());
                        }
                    });
            return suppressions;
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private String extractFingerprint(JsonNode result) {
        JsonNode fingerprints = result.get("fingerprints");
        if (fingerprints != null && fingerprints.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = fingerprints.fields();
            if (fields.hasNext()) {
                return fields.next().getValue().asText(null);
            }
        }
        JsonNode ruleId = result.get("ruleId");
        JsonNode region = result.at("/locations/0/physicalLocation/region/startLine");
        if (ruleId != null && region.isInt()) {
            return ruleId.asText() + ":" + region.asInt();
        }
        return null;
    }

    private SuppressionDecision evaluateSuppression(JsonNode suppression) {
        String expiresAt = suppression.path("expiresAt").asText(null);
        if (expiresAt != null) {
            try {
                Instant expiry = Instant.parse(expiresAt);
                if (expiry.isBefore(Instant.now())) {
                    return SuppressionDecision.EXPIRED;
                }
            } catch (Exception e) {
                return SuppressionDecision.INVALID;
            }
        }
        return SuppressionDecision.APPLY;
    }

    private ZeroFalseResult applyZeroFalseFiltering(ArrayNode candidates,
                                                    JsonNode runNode,
                                                    Path sourceRoot,
                                                    ZeroFalseSettings settings,
                                                    ScanRequest request) {
        ArrayNode filtered = mapper.createArrayNode();
        int suppressed = 0;
        int evaluated = 0;
        int limit = settings.maxFindings();
        for (JsonNode result : candidates) {
            if (evaluated >= limit) {
                filtered.add(result);
                continue;
            }
            String cweId = ZeroFalseCweResolver.resolve(result, runNode);
            ZeroFalseContext context = contextBuilder.build(sourceRoot, runNode, result, settings);
            if (context.isEmpty()) {
                filtered.add(result);
                continue;
            }
            String prompt = promptLibrary.render(cweId, context);
            Optional<ZeroFalseVerdict> verdict = zeroFalseEvaluator.evaluate(prompt);
            if (verdict.isEmpty() || verdict.get().confidencePercent() == null) {
                filtered.add(result);
                evaluated++;
                continue;
            }
            ZeroFalseVerdict zeroFalseVerdict = verdict.get();
            TriageCandidateRequest candidate = buildTriageCandidate(request, result, cweId, zeroFalseVerdict);
            Optional<TriageCandidateResponse> triageResponse = triageClient.submitCandidate(candidate);
            TriageClassification classification = triageResponse
                    .map(TriageCandidateResponse::getClassification)
                    .orElseGet(() -> classifyByThreshold(zeroFalseVerdict.confidencePercent()));
            if (classification == TriageClassification.FALSE_POSITIVE) {
                suppressed++;
            } else {
                filtered.add(result);
            }
            evaluated++;
        }
        return new ZeroFalseResult(filtered, suppressed);
    }

    private TriageCandidateRequest buildTriageCandidate(ScanRequest request,
                                                        JsonNode result,
                                                        String cweId,
                                                        ZeroFalseVerdict verdict) {
        String repository = request == null ? null : request.getRepository();
        String commitSha = request == null ? null : request.getCommitSha();
        String runId = request == null ? null : request.getRunId();
        String ruleId = result.path("ruleId").asText(null);
        String fingerprint = extractFingerprint(result);
        String filePath = result.at("/locations/0/physicalLocation/artifactLocation/uri").asText(null);
        Integer startLine = result.at("/locations/0/physicalLocation/region/startLine").isInt()
                ? result.at("/locations/0/physicalLocation/region/startLine").asInt()
                : null;
        String message = result.path("message").path("text").asText(null);
        return new TriageCandidateRequest(
                repository,
                commitSha,
                runId,
                cweId,
                ruleId,
                fingerprint,
                filePath,
                startLine,
                verdict.confidencePercent(),
                message);
    }

    private TriageClassification classifyByThreshold(Integer confidencePercent) {
        if (confidencePercent == null) {
            return TriageClassification.TRUE_POSITIVE;
        }
        int value = confidencePercent;
        if (value <= 30) {
            return TriageClassification.TRUE_POSITIVE;
        }
        if (value <= 60) {
            return TriageClassification.POTENTIAL_FALSE_POSITIVE;
        }
        return TriageClassification.FALSE_POSITIVE;
    }

    private Path extractSourceArchive(ArtifactRef sourceArchive) {
        if (sourceArchive == null || sourceArchive.getUri().startsWith("none://")) {
            return null;
        }
        try {
            Path archivePath = resolveFilePath(sourceArchive.getUri());
            Path tempDir = Files.createTempDirectory("autotriage-source-context-");
            extractTarGz(archivePath, tempDir);
            return tempDir;
        } catch (Exception e) {
            log.warnv("Failed to extract source archive for ZeroFalse: {0}", e.getMessage());
            return null;
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

    private enum SuppressionDecision {
        APPLY,
        EXPIRED,
        INVALID,
        NONE
    }

    private record ZeroFalseResult(ArrayNode filtered, int suppressed) {
    }
}
