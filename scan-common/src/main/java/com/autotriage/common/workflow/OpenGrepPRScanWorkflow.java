package com.autotriage.common.workflow;

import com.autotriage.common.model.ScanRequest;
import com.autotriage.common.model.ScanStatus;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface OpenGrepPRScanWorkflow {

    @WorkflowMethod
    void startScan(ScanRequest request);

    @QueryMethod
    ScanStatus queryStatus(String runId);

    @SignalMethod
    void cancelScan(String runId);
}
