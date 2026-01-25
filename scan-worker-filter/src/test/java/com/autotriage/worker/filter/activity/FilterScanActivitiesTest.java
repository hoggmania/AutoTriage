package com.autotriage.worker.filter.activity;

import com.autotriage.common.model.ArtifactRef;
import com.autotriage.common.model.SuppressionApplicationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterScanActivitiesTest {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static Path tempRoot;
    private static Path artifactsDir;

    @BeforeAll
    static void setupArtifactsDir() throws IOException {
        tempRoot = Files.createTempDirectory("filter-activities-test-");
        artifactsDir = tempRoot.resolve("artifacts");
        Files.createDirectories(artifactsDir);
        System.setProperty("artifacts.dir", artifactsDir.toString());
    }

    @AfterAll
    static void cleanup() throws IOException {
        System.clearProperty("artifacts.dir");
        if (tempRoot != null) {
            deleteRecursively(tempRoot);
        }
    }

    @Test
    void applySuppressions_filtersAndReportsCounts() throws Exception {
        Path rawSarif = tempRoot.resolve("raw-scan.sarif");
        ArrayNode results = mapper.createArrayNode();
        results.add(createResult("RULE-A", "fp-1", 10));
        results.add(createResult("RULE-B", "fp-expired", 11));
        results.add(createResult("RULE-C", "fp-invalid", 12));
        results.add(createResult("RULE-D", null, 42));
        results.add(createResult("RULE-E", "fp-none", 13));
        writeSarif(rawSarif, results);

        ArrayNode suppressions = mapper.createArrayNode();
        suppressions.add(createSuppression("fp-1", null));
        suppressions.add(createSuppression("fp-expired", Instant.parse("2000-01-01T00:00:00Z").toString()));
        suppressions.add(createSuppression("fp-invalid", "not-a-date"));
        suppressions.add(createSuppression("RULE-D:42", null));
        Path suppressionBundle = tempRoot.resolve("suppressions.tar.gz");
        writeSuppressionBundle(suppressionBundle, suppressions);

        FilterScanActivities activities = new FilterScanActivities();
        SuppressionApplicationResult result = activities.applySuppressions(
                new ArtifactRef(rawSarif.toUri().toString(), "sarif-raw"),
                new ArtifactRef(suppressionBundle.toUri().toString(), "suppression-bundle"));

        Path finalSarifPath = Path.of(URI.create(result.getFinalSarif().getUri()));
        JsonNode finalSarif = mapper.readTree(Files.readString(finalSarifPath, StandardCharsets.UTF_8));
        JsonNode finalResults = finalSarif.at("/runs/0/results");
        assertEquals(2, finalResults.size());
        assertTrue(containsFingerprint(finalResults, "fp-invalid"));
        assertTrue(containsFingerprint(finalResults, "fp-none"));

        Path reportPath = Path.of(URI.create(result.getSuppressionReport().getUri()));
        JsonNode report = mapper.readTree(Files.readString(reportPath, StandardCharsets.UTF_8));
        assertEquals(2, report.path("suppressedCount").asInt());
        assertEquals(1, report.path("expiredCount").asInt());
        assertEquals(1, report.path("invalidCount").asInt());
    }

    @Test
    void applySuppressions_withNoBundleLeavesResultsUntouched() throws Exception {
        Path rawSarif = tempRoot.resolve("raw-no-suppressions.sarif");
        ArrayNode results = mapper.createArrayNode();
        results.add(createResult("RULE-A", "fp-1", 10));
        results.add(createResult("RULE-B", "fp-2", 20));
        writeSarif(rawSarif, results);

        FilterScanActivities activities = new FilterScanActivities();
        SuppressionApplicationResult result = activities.applySuppressions(
                new ArtifactRef(rawSarif.toUri().toString(), "sarif-raw"),
                new ArtifactRef("none://suppressions", "suppression-bundle"));

        Path finalSarifPath = Path.of(URI.create(result.getFinalSarif().getUri()));
        JsonNode finalSarif = mapper.readTree(Files.readString(finalSarifPath, StandardCharsets.UTF_8));
        JsonNode finalResults = finalSarif.at("/runs/0/results");
        assertEquals(2, finalResults.size());
        assertTrue(containsFingerprint(finalResults, "fp-1"));
        assertTrue(containsFingerprint(finalResults, "fp-2"));

        Path reportPath = Path.of(URI.create(result.getSuppressionReport().getUri()));
        JsonNode report = mapper.readTree(Files.readString(reportPath, StandardCharsets.UTF_8));
        assertEquals(0, report.path("suppressedCount").asInt());
        assertEquals(0, report.path("expiredCount").asInt());
        assertEquals(0, report.path("invalidCount").asInt());
    }

    private static ObjectNode createResult(String ruleId, String fingerprint, int startLine) {
        ObjectNode result = mapper.createObjectNode();
        result.put("ruleId", ruleId);
        if (fingerprint != null) {
            ObjectNode fingerprints = mapper.createObjectNode();
            fingerprints.put("primary", fingerprint);
            result.set("fingerprints", fingerprints);
        }
        ObjectNode region = mapper.createObjectNode();
        region.put("startLine", startLine);
        ObjectNode physicalLocation = mapper.createObjectNode();
        physicalLocation.set("region", region);
        ObjectNode location = mapper.createObjectNode();
        location.set("physicalLocation", physicalLocation);
        ArrayNode locations = mapper.createArrayNode();
        locations.add(location);
        result.set("locations", locations);
        return result;
    }

    private static ObjectNode createSuppression(String fingerprint, String expiresAt) {
        ObjectNode suppression = mapper.createObjectNode();
        suppression.put("fingerprint", fingerprint);
        if (expiresAt != null) {
            suppression.put("expiresAt", expiresAt);
        }
        return suppression;
    }

    private static void writeSarif(Path sarifPath, ArrayNode results) throws IOException {
        ObjectNode sarif = mapper.createObjectNode();
        sarif.put("version", "2.1.0");
        ObjectNode run = mapper.createObjectNode();
        run.set("results", results);
        ArrayNode runs = mapper.createArrayNode();
        runs.add(run);
        sarif.set("runs", runs);
        Files.writeString(sarifPath, mapper.writeValueAsString(sarif), StandardCharsets.UTF_8);
    }

    private static void writeSuppressionBundle(Path bundlePath, ArrayNode suppressions) throws IOException {
        String content = mapper.writeValueAsString(suppressions);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        try (OutputStream fileOut = Files.newOutputStream(bundlePath);
             BufferedOutputStream buffered = new BufferedOutputStream(fileOut);
             GzipCompressorOutputStream gzipOut = new GzipCompressorOutputStream(buffered);
             TarArchiveOutputStream tarOut = new TarArchiveOutputStream(gzipOut)) {
            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            TarArchiveEntry entry = new TarArchiveEntry("suppressions.yaml");
            entry.setSize(bytes.length);
            tarOut.putArchiveEntry(entry);
            tarOut.write(bytes);
            tarOut.closeArchiveEntry();
        }
    }

    private static boolean containsFingerprint(JsonNode results, String fingerprint) {
        for (JsonNode result : results) {
            JsonNode fingerprints = result.get("fingerprints");
            if (fingerprints != null && fingerprints.isObject()) {
                if (fingerprint.equals(fingerprints.elements().next().asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void deleteRecursively(Path root) throws IOException {
        Files.walk(root)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to delete " + path, e);
                    }
                });
    }
}
