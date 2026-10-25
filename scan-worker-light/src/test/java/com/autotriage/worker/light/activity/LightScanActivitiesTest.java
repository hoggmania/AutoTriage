package com.autotriage.worker.light.activity;

import com.autotriage.common.model.ArtifactRef;
import com.autotriage.common.artifact.ArtifactContent;
import com.autotriage.test.TestArtifactStore;
import com.autotriage.common.model.ScanRequest;
import com.autotriage.common.model.ScanState;
import com.autotriage.common.model.ScanStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.temporal.failure.ApplicationFailure;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightScanActivitiesTest {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Path tempRoot = createTempRoot();
    private static final TestArtifactStore artifactStore = new TestArtifactStore();

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

        LightScanActivities activities = new LightScanActivities(artifactStore);
        ArtifactRef ref = artifactStore.put(new ArtifactContent(Files.readAllBytes(sarifPath), "sarif-final",
                "application/sarif+json", "run-1", "test"));
        ScanStatus status = activities.computeVerdict("run-1", ref);

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

        LightScanActivities activities = new LightScanActivities(artifactStore);
        ArtifactRef ref = artifactStore.put(new ArtifactContent(Files.readAllBytes(sarifPath), "sarif-final",
                "application/sarif+json", "run-2", "test"));
        ScanStatus status = activities.computeVerdict("run-2", ref);

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

    @Test
    void buildCloneUrl_refusesToSendTokenToUnlistedHost() {
        System.setProperty("git.clone.token", "secret-token");
        try {
            InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                    () -> invokeBuildCloneUrl("https://attacker.example/example/repo.git"));

            assertInstanceOf(IllegalArgumentException.class, thrown.getCause());
            assertFalse(thrown.getCause().getMessage().contains("secret-token"));
        } finally {
            System.clearProperty("git.clone.token");
        }
    }

    @Test
    void resolveRepoSource_rejectsLocalRepositoryWithoutRetrying() {
        LightScanActivities activities = new LightScanActivities(artifactStore);
        ScanRequest request = new ScanRequest("run-local", "file:///etc", "0123456789abcdef0123456789abcdef01234567",
                null, null, null);

        ApplicationFailure failure = assertThrows(ApplicationFailure.class, () -> activities.resolveRepoSource(request));

        assertTrue(failure.isNonRetryable());
        assertEquals("InvalidScanInput", failure.getType());
    }

    @Test
    void resolveRepoSource_rejectsOptionLikeCommitSha() {
        LightScanActivities activities = new LightScanActivities(artifactStore);
        ScanRequest request = new ScanRequest("run-option", "https://github.com/example/repo.git", "--orphan=evil",
                null, null, null);

        ApplicationFailure failure = assertThrows(ApplicationFailure.class, () -> activities.resolveRepoSource(request));

        assertTrue(failure.isNonRetryable());
    }

    @Test
    void fetchSuppressionBundle_rejectsOptionLikeHeadRefBeforeCloning() {
        LightScanActivities activities = new LightScanActivities(artifactStore);

        ApplicationFailure failure = assertThrows(ApplicationFailure.class,
                () -> activities.fetchSuppressionBundle("https://github.com/example/repo.git", "--orphan=evil", "main"));

        assertTrue(failure.isNonRetryable());
    }

    @Test
    void gitCommands_separateOptionsFromCallerInput() {
        Path workspace = tempRoot.resolve("ws");

        List<String> clone = List.of(LightScanActivities.cloneCommand("https://github.com/example/repo.git", workspace));
        List<String> checkout = List.of(LightScanActivities.checkoutCommand(workspace, "main"));

        assertTrue(clone.contains("protocol.allow=never"));
        assertTrue(clone.contains("protocol.https.allow=always"));
        assertFalse(clone.stream().anyMatch(arg -> arg.startsWith("protocol.file") || arg.startsWith("protocol.ext")));
        assertEquals(clone.indexOf("--") + 1, clone.indexOf("https://github.com/example/repo.git"));
        assertEquals(checkout.indexOf("--end-of-options") + 1, checkout.indexOf("main"));
    }

    @Test
    void createTarGzArchive_doesNotFollowSymlinksOutOfTheRepository() throws Exception {
        Path outside = Files.createDirectories(tempRoot.resolve("outside"));
        Path secret = Files.writeString(outside.resolve("token"), "worker-service-account-token");
        Path repo = Files.createDirectories(tempRoot.resolve("repo-with-symlink"));
        Files.writeString(repo.resolve("App.java"), "class App {}");
        createSymlinkOrSkip(repo.resolve("leak.txt"), secret);
        createSymlinkOrSkip(repo.resolve("leakdir"), outside);
        Path archive = tempRoot.resolve("repo-with-symlink.tar.gz");

        LightScanActivities.createTarGzArchive(repo, archive);

        Map<String, String> entries = readArchive(archive);
        assertEquals("class App {}", entries.get("App.java"));
        assertFalse(entries.containsKey("leak.txt"));
        assertFalse(entries.keySet().stream().anyMatch(name -> name.startsWith("leakdir")));
        assertFalse(entries.values().stream().anyMatch(content -> content.contains("worker-service-account-token")));
    }

    @Test
    void isInsideWorkspace_rejectsSymlinkedDirectoryPointingOutsideClone() throws Exception {
        Path outside = Files.createDirectories(tempRoot.resolve("outside-suppressions"));
        Path workspace = Files.createDirectories(tempRoot.resolve("clone"));
        Path inside = Files.createDirectories(workspace.resolve("inside"));
        Path linked = workspace.resolve(".opengrep");
        createSymlinkOrSkip(linked, outside);

        assertTrue(LightScanActivities.isInsideWorkspace(inside, workspace));
        assertFalse(LightScanActivities.isInsideWorkspace(linked, workspace));
    }

    private static void createSymlinkOrSkip(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            // Windows needs Developer Mode or admin rights to create symlinks; CI runs on Linux.
            Assumptions.abort("Symbolic links are not available: " + e.getMessage());
        }
    }

    private static Map<String, String> readArchive(Path archive) throws IOException {
        Map<String, String> entries = new HashMap<>();
        try (TarArchiveInputStream tarIn = new TarArchiveInputStream(
                new GzipCompressorInputStream(Files.newInputStream(archive)))) {
            TarArchiveEntry entry;
            while ((entry = tarIn.getNextEntry()) != null) {
                entries.put(entry.getName(), entry.isDirectory() ? ""
                        : new String(tarIn.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    private static String invokeBuildCloneUrl(String repository) throws Exception {
        Method method = LightScanActivities.class.getDeclaredMethod("buildCloneUrl", String.class);
        method.setAccessible(true);
        return (String) method.invoke(new LightScanActivities(artifactStore), repository);
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
