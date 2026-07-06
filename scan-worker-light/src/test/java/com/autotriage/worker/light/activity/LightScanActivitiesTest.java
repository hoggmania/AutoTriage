package com.autotriage.worker.light.activity;

import com.autotriage.common.model.ArtifactRef;
import com.autotriage.common.model.ScanState;
import com.autotriage.common.model.ScanStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightScanActivitiesTest {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Path tempRoot = createTempRoot();

    @AfterAll
    static void cleanup() throws IOException {
        clearGatePolicy();
        System.clearProperty("git.clone.token");
        deleteRecursively(tempRoot);
    }

    @Test
    void computeVerdict_failsWhenThresholdExceeded() throws Exception {
        setGatePolicy("false", "0", "10", "10");
        Path sarifPath = tempRoot.resolve("final-fail.sarif");
        ArrayNode results = mapper.createArrayNode();
        results.add(createResult("error", null));
        writeSarif(sarifPath, results);

        LightScanActivities activities = new LightScanActivities();
        ScanStatus status = activities.computeVerdict("run-1", new ArtifactRef(sarifPath.toUri().toString(), "sarif-final"));

        assertEquals(ScanState.COMPLETED, status.getState());
        assertTrue(status.getMessage().contains("Verdict: FAIL"));
    }

    @Test
    void computeVerdict_passesWhenWithinThresholds() throws Exception {
        setGatePolicy("false", "1", "2", "5");
        Path sarifPath = tempRoot.resolve("final-pass.sarif");
        ArrayNode results = mapper.createArrayNode();
        results.add(createResult("warning", null));
        results.add(createResult("note", "MEDIUM"));
        writeSarif(sarifPath, results);

        LightScanActivities activities = new LightScanActivities();
        ScanStatus status = activities.computeVerdict("run-2", new ArtifactRef(sarifPath.toUri().toString(), "sarif-final"));

        assertEquals(ScanState.COMPLETED, status.getState());
        assertTrue(status.getMessage().contains("Verdict: PASS"));
    }

    @Test
    void buildCloneUrl_injectsEncodedTokenForHttpsRepository() throws Exception {
        System.setProperty("git.clone.token", "tok:en@with space");
        try {
            String cloneUrl = invokeBuildCloneUrl("https://github.com/example/repo.git");

            assertEquals("https://x-access-token:tok%3Aen%40with%20space@github.com/example/repo.git", cloneUrl);
        } finally {
            System.clearProperty("git.clone.token");
        }
    }

    @Test
    void buildCloneUrl_leavesNonHttpsRepositoryUnchanged() throws Exception {
        System.setProperty("git.clone.token", "secret-token");
        try {
            String cloneUrl = invokeBuildCloneUrl("git@github.com:example/repo.git");

            assertEquals("git@github.com:example/repo.git", cloneUrl);
        } finally {
            System.clearProperty("git.clone.token");
        }
    }

    private static String invokeBuildCloneUrl(String repository) throws Exception {
        Method method = LightScanActivities.class.getDeclaredMethod("buildCloneUrl", String.class);
        method.setAccessible(true);
        return (String) method.invoke(new LightScanActivities(), repository);
    }

    private static void setGatePolicy(String failOnAny, String maxHigh, String maxMedium, String maxLow) {
        System.setProperty("gate.policy.fail-on-any", failOnAny);
        System.setProperty("gate.policy.max-high", maxHigh);
        System.setProperty("gate.policy.max-medium", maxMedium);
        System.setProperty("gate.policy.max-low", maxLow);
    }

    private static void clearGatePolicy() {
        System.clearProperty("gate.policy.fail-on-any");
        System.clearProperty("gate.policy.max-high");
        System.clearProperty("gate.policy.max-medium");
        System.clearProperty("gate.policy.max-low");
    }

    private static ObjectNode createResult(String level, String severity) {
        ObjectNode result = mapper.createObjectNode();
        result.put("level", level);
        if (severity != null) {
            ObjectNode properties = mapper.createObjectNode();
            properties.put("severity", severity);
            result.set("properties", properties);
        }
        return result;
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

    private static Path createTempRoot() {
        try {
            return Files.createTempDirectory("light-activities-test-");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create temp dir", e);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null) {
            return;
        }
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
