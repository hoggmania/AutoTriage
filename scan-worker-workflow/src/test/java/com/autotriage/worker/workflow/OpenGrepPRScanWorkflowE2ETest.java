package com.autotriage.worker.workflow;

import com.autotriage.common.activity.ScanActivities;
import com.autotriage.common.model.ArtifactRef;
import com.autotriage.common.model.ScanRequest;
import com.autotriage.common.model.ScanState;
import com.autotriage.common.model.ScanStatus;
import com.autotriage.common.model.SuppressionApplicationResult;
import com.autotriage.common.model.SuppressionBundle;
import com.autotriage.common.workflow.OpenGrepPRScanWorkflow;
import io.temporal.activity.Activity;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.common.WorkflowExecutionHistory;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.WorkflowReplayer;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenGrepPRScanWorkflowE2ETest {

    private static final String WORKFLOW_QUEUE = "scan-workflows";
    private TestWorkflowEnvironment environment;
    private RecordingActivities activities;

    @BeforeEach
    void setUp() {
        environment = TestWorkflowEnvironment.newInstance();
        activities = new RecordingActivities();

        Worker workflowWorker = environment.newWorker(WORKFLOW_QUEUE);
        workflowWorker.registerWorkflowImplementationTypes(OpenGrepPRScanWorkflowImpl.class);
        environment.newWorker("scan-light").registerActivitiesImplementations(activities.lightWorker());
        environment.newWorker("scan-filter").registerActivitiesImplementations(activities.filterWorker());
        environment.newWorker("scan-opengrep").registerActivitiesImplementations(activities.openGrepWorker());
        environment.start();
    }

    @AfterEach
    void tearDown() {
        if (environment != null) {
            environment.close();
        }
    }

    @Test
    void retriesTransientFailureOnAssignedQueuesAndReplaysHistory() throws Exception {
        activities.resolveFailuresRemaining.set(2);
        String workflowId = "durability-retry";
        OpenGrepPRScanWorkflow workflow = newWorkflow(workflowId);

        workflow.startScan(request("retry-run"));

        ScanStatus status = workflow.queryStatus("retry-run");
        assertEquals(ScanState.COMPLETED, status.getState(), status.getMessage() + "; executions=" + activities.executions);
        assertEquals(3, activities.resolveAttempts.get());
        assertEquals(List.of(
                "resolveRepoSource@scan-light",
                "resolveRepoSource@scan-light",
                "resolveRepoSource@scan-light",
                "fetchSuppressionBundle@scan-light",
                "verifySuppressionSignature@scan-light",
                "runOpenGrep@scan-opengrep",
                "applySuppressions@scan-filter",
                "uploadResults@scan-light",
                "computeVerdict@scan-light"), activities.executions);

        WorkflowExecutionHistory history = environment.getWorkflowClient().fetchHistory(workflowId);
        WorkflowReplayer.replayWorkflowExecution(history, OpenGrepPRScanWorkflowImpl.class);
    }

    @Test
    void cancelSignalPropagatesToHeartbeatActivityAndStopsLaterActivities() throws Exception {
        activities.blockOpenGrep.set(true);
        OpenGrepPRScanWorkflow workflow = newWorkflow("durability-cancel");
        WorkflowClient.start(workflow::startScan, request("cancel-run"));
        boolean openGrepStarted = activities.openGrepStarted.await(10, TimeUnit.SECONDS);
        if (!openGrepStarted) {
            WorkflowStub.fromTyped(workflow).getResult(Void.class);
        }
        assertTrue(openGrepStarted,
                () -> "OpenGrep did not start; status=" + workflow.queryStatus("cancel-run").getState()
                        + ", message=" + workflow.queryStatus("cancel-run").getMessage()
                        + ", executions=" + activities.executions);

        workflow.cancelScan("cancel-run");
        WorkflowStub.fromTyped(workflow).getResult(Void.class);

        ScanStatus status = workflow.queryStatus("cancel-run");
        assertEquals(ScanState.CANCELED, status.getState(), status.getMessage() + "; executions=" + activities.executions);
        assertTrue(activities.openGrepCancellationObserved.get(), "heartbeat cancellation must reach the activity");
        assertFalse(activities.executions.stream().anyMatch(entry -> entry.startsWith("applySuppressions")));
        assertFalse(activities.executions.stream().anyMatch(entry -> entry.startsWith("uploadResults")));
        assertFalse(activities.executions.stream().anyMatch(entry -> entry.startsWith("computeVerdict")));
    }

    private OpenGrepPRScanWorkflow newWorkflow(String workflowId) {
        return environment.getWorkflowClient().newWorkflowStub(
                OpenGrepPRScanWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(workflowId)
                        .setTaskQueue(WORKFLOW_QUEUE)
                        .build());
    }

    private static ScanRequest request(String runId) {
        return new ScanRequest(runId, "example/repository", "abc123", 42, "feature", "main");
    }

    private static final class RecordingActivities {
        private final AtomicInteger resolveAttempts = new AtomicInteger();
        private final AtomicInteger resolveFailuresRemaining = new AtomicInteger();
        private final AtomicBoolean blockOpenGrep = new AtomicBoolean();
        private final AtomicBoolean openGrepCancellationObserved = new AtomicBoolean();
        private final CountDownLatch openGrepStarted = new CountDownLatch(1);
        private final List<String> executions = new CopyOnWriteArrayList<>();

        ScanActivities lightWorker() {
            return new UnsupportedScanActivities() {
                @Override
                public ArtifactRef resolveRepoSource(ScanRequest request) {
                    record("resolveRepoSource", "scan-light");
                    resolveAttempts.incrementAndGet();
                    if (resolveFailuresRemaining.getAndDecrement() > 0) {
                        throw ApplicationFailure.newFailure("transient", "TransientFailure");
                    }
                    return artifact("source");
                }

                @Override
                public SuppressionBundle fetchSuppressionBundle(String repository, String headRef, String baseRef) {
                    record("fetchSuppressionBundle", "scan-light");
                    return SuppressionBundle.none();
                }

                @Override
                public boolean verifySuppressionSignature(ArtifactRef bundle) {
                    record("verifySuppressionSignature", "scan-light");
                    return true;
                }

                @Override
                public void uploadResults(String runId, ArtifactRef finalSarif, ArtifactRef rawSarif,
                                          ArtifactRef suppressionReport) {
                    record("uploadResults", "scan-light");
                }

                @Override
                public ScanStatus computeVerdict(String runId, ArtifactRef finalSarif) {
                    record("computeVerdict", "scan-light");
                    return new ScanStatus(runId, ScanState.COMPLETED, "complete");
                }
            };
        }

        ScanActivities filterWorker() {
            return new UnsupportedScanActivities() {
                @Override
                public SuppressionApplicationResult applySuppressions(ArtifactRef rawSarif,
                                                                       ArtifactRef suppressionBundle,
                                                                       ArtifactRef sourceArchive,
                                                                       ScanRequest request) {
                    record("applySuppressions", "scan-filter");
                    return new SuppressionApplicationResult(artifact("final"), artifact("report"));
                }
            };
        }

        ScanActivities openGrepWorker() {
            return new UnsupportedScanActivities() {
                @Override
                public ArtifactRef runOpenGrep(ArtifactRef source, String runId) {
                    record("runOpenGrep", "scan-opengrep");
                    openGrepStarted.countDown();
                    while (blockOpenGrep.get()) {
                        try {
                            Activity.getExecutionContext().heartbeat("blocked");
                            Thread.sleep(10);
                        } catch (RuntimeException cancellation) {
                            openGrepCancellationObserved.set(true);
                            throw cancellation;
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(interrupted);
                        }
                    }
                    return artifact("raw");
                }
            };
        }

        private void record(String method, String queue) {
            executions.add(method + "@" + queue);
        }

        private static ArtifactRef artifact(String kind) {
            byte[] content = kind.getBytes(StandardCharsets.UTF_8);
            String digest;
            try {
                digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
            } catch (java.security.NoSuchAlgorithmException impossible) {
                throw new IllegalStateException(impossible);
            }
            String objectKey = ArtifactRef.contentAddressedKey(digest);
            return new ArtifactRef("artifact://test/" + objectKey, kind, digest, content.length,
                    "application/octet-stream", objectKey);
        }
    }

    public interface QueueActivities extends ScanActivities {
        @Override ArtifactRef resolveRepoSource(ScanRequest request);
        @Override SuppressionBundle fetchSuppressionBundle(String repository, String headRef, String baseRef);
        @Override boolean verifySuppressionSignature(ArtifactRef bundle);
        @Override ArtifactRef runOpenGrep(ArtifactRef source, String runId);
        @Override SuppressionApplicationResult applySuppressions(ArtifactRef rawSarif, ArtifactRef suppressionBundle,
                                                                 ArtifactRef sourceArchive, ScanRequest request);
        @Override void uploadResults(String runId, ArtifactRef finalSarif, ArtifactRef rawSarif,
                                     ArtifactRef suppressionReport);
        @Override ScanStatus computeVerdict(String runId, ArtifactRef finalSarif);
    }

    private abstract static class UnsupportedScanActivities implements QueueActivities {
        private UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("wrong activity worker");
        }

        @Override public ArtifactRef resolveRepoSource(ScanRequest request) { throw unsupported(); }
        @Override public SuppressionBundle fetchSuppressionBundle(String repository, String headRef, String baseRef) { throw unsupported(); }
        @Override public boolean verifySuppressionSignature(ArtifactRef bundle) { throw unsupported(); }
        @Override public ArtifactRef runOpenGrep(ArtifactRef source, String runId) { throw unsupported(); }
        @Override public SuppressionApplicationResult applySuppressions(ArtifactRef rawSarif, ArtifactRef suppressionBundle,
                                                                        ArtifactRef sourceArchive, ScanRequest request) { throw unsupported(); }
        @Override public void uploadResults(String runId, ArtifactRef finalSarif, ArtifactRef rawSarif,
                                            ArtifactRef suppressionReport) { throw unsupported(); }
        @Override public ScanStatus computeVerdict(String runId, ArtifactRef finalSarif) { throw unsupported(); }
    }
}
