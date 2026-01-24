package com.autotriage.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public final class ScanRequest {
    private final String runId;
    private final String repository;
    private final String commitSha;
    private final Integer prNumber;

    @JsonCreator
    public ScanRequest(
            @JsonProperty("runId") String runId,
            @JsonProperty("repository") String repository,
            @JsonProperty("commitSha") String commitSha,
            @JsonProperty("prNumber") Integer prNumber) {
        this.runId = Objects.requireNonNull(runId, "runId");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.commitSha = Objects.requireNonNull(commitSha, "commitSha");
        this.prNumber = prNumber;
    }

    public String getRunId() {
        return runId;
    }

    public String getRepository() {
        return repository;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public Integer getPrNumber() {
        return prNumber;
    }
}
