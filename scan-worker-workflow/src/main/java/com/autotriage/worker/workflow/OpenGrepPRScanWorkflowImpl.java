package com.autotriage.worker.workflow;

import com.autotriage.common.model.ScanRequest;
import com.autotriage.common.model.ScanState;
import com.autotriage.common.model.ScanStatus;
import com.autotriage.common.activity.ScanActivities;
import com.autotriage.common.model.ArtifactRef;
import com.autotriage.common.model.SuppressionApplicationResult;
import com.autotriage.common.model.SuppressionBundle;
import com.autotriage.common.model.SuppressionSource;
import com.autotriage.common.workflow.OpenGrepPRScanWorkflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

public class OpenGrepPRScanWorkflowImpl implements OpenGrepPRScanWorkflow {

    private ScanStatus status = new ScanStatus("unknown", ScanState.RUNNING, "Not started");
    private boolean cancelRequested;

    @Override
    public void startScan(ScanRequest request) {
        updateStatus(request.getRunId(), ScanState.RUNNING, "Workflow started");
        if (cancelRequested) {
            updateStatus(request.getRunId(), ScanState.CANCELED, "Canceled before start");
            return;
        }

        ScanActivities lightActivities = Workflow.newActivityStub(
                ScanActivities.class,
                ActivityOptions.newBuilder()
                        .setTaskQueue("scan-light")
                        .setStartToCloseTimeout(Duration.ofMinutes(5))
                        .setRetryOptions(RetryOptions.newBuilder()
                                .setInitialInterval(Duration.ofSeconds(2))
                                .setMaximumInterval(Duration.ofMinutes(1))
                                .setBackoffCoefficient(2.0)
                                .setMaximumAttempts(6)
                                .build())
                        .build());

        ScanActivities filterActivities = Workflow.newActivityStub(
                ScanActivities.class,
                ActivityOptions.newBuilder()
                        .setTaskQueue("scan-filter")
                        .setStartToCloseTimeout(Duration.ofMinutes(10))
                        .setRetryOptions(RetryOptions.newBuilder()
                                .setInitialInterval(Duration.ofSeconds(5))
                                .setMaximumInterval(Duration.ofMinutes(2))
                                .setBackoffCoefficient(2.0)
                                .setMaximumAttempts(4)
                                .build())
                        .build());

        ScanActivities openGrepActivities = Workflow.newActivityStub(
                ScanActivities.class,
                ActivityOptions.newBuilder()
                        .setTaskQueue("scan-opengrep")
                        .setStartToCloseTimeout(Duration.ofMinutes(30))
                        .setHeartbeatTimeout(Duration.ofSeconds(30))
                        .setRetryOptions(RetryOptions.newBuilder()
                                .setInitialInterval(Duration.ofSeconds(10))
                                .setMaximumInterval(Duration.ofMinutes(5))
                                .setBackoffCoefficient(2.0)
                                .setMaximumAttempts(3)
                                .build())
                        .build());

        try {
            updateStatus(request.getRunId(), ScanState.RUNNING, "Resolving repository");
            ArtifactRef source = lightActivities.resolveRepoSource(request);
            if (cancelRequested) {
                updateStatus(request.getRunId(), ScanState.CANCELED, "Canceled after repo resolve");
                return;
            }

            updateStatus(request.getRunId(), ScanState.RUNNING, "Fetching suppressions");
            String headRef = resolveHeadRef(request);
            SuppressionBundle suppressionBundle = lightActivities.fetchSuppressionBundle(
                    request.getRepository(),
                    headRef,
                    request.getBaseRef());
            ArtifactRef suppressionArtifact = suppressionBundle.getBundle();
            if (suppressionBundle.getSource() != SuppressionSource.NONE) {
                updateStatus(request.getRunId(), ScanState.RUNNING, "Suppressions sourced from " + suppressionBundle.getSource());
            } else {
                updateStatus(request.getRunId(), ScanState.RUNNING, "No suppressions found");
            }

            updateStatus(request.getRunId(), ScanState.RUNNING, "Verifying suppressions");
            boolean verified = lightActivities.verifySuppressionSignature(suppressionArtifact);
            if (!verified) {
                suppressionArtifact = new ArtifactRef("none://suppressions", "suppression-bundle");
            }
            if (cancelRequested) {
                updateStatus(request.getRunId(), ScanState.CANCELED, "Canceled after suppression verification");
                return;
            }

            updateStatus(request.getRunId(), ScanState.RUNNING, "Running OpenGrep");
            ArtifactRef rawSarif = openGrepActivities.runOpenGrep(source, request.getRunId());
            if (cancelRequested) {
                updateStatus(request.getRunId(), ScanState.CANCELED, "Canceled after OpenGrep run");
                return;
            }

            updateStatus(request.getRunId(), ScanState.RUNNING, "Applying suppressions");
            SuppressionApplicationResult suppressionResult = filterActivities.applySuppressions(rawSarif, suppressionArtifact, source);

            updateStatus(request.getRunId(), ScanState.RUNNING, "Uploading results");
            lightActivities.uploadResults(request.getRunId(), suppressionResult.getFinalSarif(), rawSarif, suppressionResult.getSuppressionReport());

            updateStatus(request.getRunId(), ScanState.RUNNING, "Computing verdict");
            ScanStatus verdict = lightActivities.computeVerdict(request.getRunId(), suppressionResult.getFinalSarif());
            updateStatus(request.getRunId(), verdict.getState(), verdict.getMessage());
        } catch (Exception e) {
            Workflow.getLogger(OpenGrepPRScanWorkflowImpl.class)
                    .error("Workflow failed for runId=" + request.getRunId(), e);
            updateStatus(request.getRunId(), ScanState.FAILED, "Workflow failed: " + e.getClass().getSimpleName());
        }
    }

    @Override
    public ScanStatus queryStatus(String runId) {
        return status;
    }

    @Override
    public void cancelScan(String runId) {
        cancelRequested = true;
        updateStatus(runId, ScanState.CANCELED, "Cancel requested");
    }

    private void updateStatus(String runId, ScanState state, String message) {
        status = new ScanStatus(runId, state, message);
    }

    private String resolveHeadRef(ScanRequest request) {
        String headRef = request.getHeadRef();
        if (headRef == null || headRef.isBlank()) {
            return request.getCommitSha();
        }
        return headRef;
    }
}
