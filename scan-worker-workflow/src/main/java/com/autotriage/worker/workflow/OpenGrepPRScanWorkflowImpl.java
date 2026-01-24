package com.autotriage.worker.workflow;

import com.autotriage.common.model.ScanRequest;
import com.autotriage.common.model.ScanState;
import com.autotriage.common.model.ScanStatus;
import com.autotriage.common.workflow.OpenGrepPRScanWorkflow;
import io.temporal.workflow.Workflow;

public class OpenGrepPRScanWorkflowImpl implements OpenGrepPRScanWorkflow {

    private ScanStatus status = new ScanStatus("unknown", ScanState.RUNNING, "Not started");
    private boolean cancelRequested;

    @Override
    public void startScan(ScanRequest request) {
        status = new ScanStatus(request.getRunId(), ScanState.RUNNING, "Workflow started");
        if (cancelRequested) {
            status = new ScanStatus(request.getRunId(), ScanState.CANCELED, "Canceled before start");
            return;
        }
        Workflow.getLogger(OpenGrepPRScanWorkflowImpl.class)
                .info("Workflow stub completed for runId=" + request.getRunId());
        status = new ScanStatus(request.getRunId(), ScanState.COMPLETED, "Workflow stub completed");
    }

    @Override
    public ScanStatus queryStatus(String runId) {
        return status;
    }

    @Override
    public void cancelScan(String runId) {
        cancelRequested = true;
        status = new ScanStatus(runId, ScanState.CANCELED, "Cancel requested");
    }
}
