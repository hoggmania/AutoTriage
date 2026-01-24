package com.autotriage.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public final class ScanStatus {
    private final String runId;
    private final ScanState state;
    private final String message;

    @JsonCreator
    public ScanStatus(
            @JsonProperty("runId") String runId,
            @JsonProperty("state") ScanState state,
            @JsonProperty("message") String message) {
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
