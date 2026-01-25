package com.autotriage.worker.filter.activity;

import com.autotriage.common.activity.ScanActivities;
import com.autotriage.common.model.ArtifactRef;
import com.autotriage.common.model.ScanRequest;
import com.autotriage.common.model.ScanStatus;
import com.autotriage.common.model.SuppressionApplicationResult;
import com.autotriage.worker.filter.model.SuppressionReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

public class FilterScanActivities implements ScanActivities {

    private static final Logger log = Logger.getLogger(FilterScanActivities.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public ArtifactRef resolveRepoSource(ScanRequest request) {
        throw new UnsupportedOperationException("resolveRepoSource is handled by light worker");
    }

    @Override
    public ArtifactRef fetchSuppressionBundle(String repository, String ref) {
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
    public SuppressionApplicationResult applySuppressions(ArtifactRef rawSarif, ArtifactRef suppressionBundle) {
        log.infov("applySuppressions rawUri={0} suppressionUri={1}", rawSarif.getUri(), suppressionBundle.getUri());
        try {
            Path rawPath = resolveFilePath(rawSarif.getUri());
            JsonNode sarif = mapper.readTree(Files.readString(rawPath, StandardCharsets.UTF_8));
            ArrayNode results = (ArrayNode) sarif.at("/runs/0/results");
            if (results == null) {
                results = mapper.createArrayNode();
            }
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
            ((ObjectNode) sarif.at("/runs/0")).set("results", filtered);
            Path artifactsDir = resolveArtifactsDir();
            Files.createDirectories(artifactsDir);
            Path finalSarifPath = artifactsDir.resolve("final-" + rawPath.getFileName().toString());
            Files.writeString(finalSarifPath, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(sarif), StandardCharsets.UTF_8);
            SuppressionReport report = new SuppressionReport(suppressed, expired, invalid);
            Path reportPath = artifactsDir.resolve("suppression-report-" + rawPath.getFileName().toString().replace(".sarif", ".json"));
            Files.writeString(reportPath, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report), StandardCharsets.UTF_8);
            return new SuppressionApplicationResult(
                    new ArtifactRef(finalSarifPath.toUri().toString(), "sarif-final"),
                    new ArtifactRef(reportPath.toUri().toString(), "suppression-report"));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to apply suppressions", e);
        }
    }

    @Override
    public void uploadResults(String runId, ArtifactRef finalSarif, ArtifactRef rawSarif) {
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

    private void extractTarGz(Path archive, Path destDir) throws IOException {
        try (InputStream fileIn = Files.newInputStream(archive);
             BufferedInputStream buffered = new BufferedInputStream(fileIn);
             GzipCompressorInputStream gzipIn = new GzipCompressorInputStream(buffered);
             TarArchiveInputStream tarIn = new TarArchiveInputStream(gzipIn)) {
            TarArchiveEntry entry;
            while ((entry = tarIn.getNextTarEntry()) != null) {
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
}
