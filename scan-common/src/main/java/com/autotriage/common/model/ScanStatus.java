package com.autotriage.common.model;

import java.util.Objects;

public final class ScanStatus {
    private final String runId;
    private final ScanState state;
    private final String message;

    public ScanStatus(String runId, ScanState state, String message) {
        this.runId = Objects.requireNonNull(runId, "runId");
        this.state = Objects.requireNonNull(state, "state");
        this.message = message;
    }

    public String getRunId() {
        return runId;
    }

    public ScanState getState() {
        return state;
    }

    public String getMessage() {
        return message;
    }

    public static ScanStatus running(String runId) {
        return new ScanStatus(runId, ScanState.RUNNING, "In progress");
    }
}
