package com.autotriage.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class TriageCandidateRequest {
    private final String repository;
    private final String commitSha;
    private final String runId;
    private final String cweId;
    private final String ruleId;
    private final String fingerprint;
    private final String filePath;
    private final Integer startLine;
    private final Integer confidencePercent;
    private final String message;

    @JsonCreator
    public TriageCandidateRequest(
            @JsonProperty("repository") String repository,
            @JsonProperty("commitSha") String commitSha,
            @JsonProperty("runId") String runId,
            @JsonProperty("cweId") String cweId,
            @JsonProperty("ruleId") String ruleId,
            @JsonProperty("fingerprint") String fingerprint,
            @JsonProperty("filePath") String filePath,
            @JsonProperty("startLine") Integer startLine,
            @JsonProperty("confidencePercent") Integer confidencePercent,
            @JsonProperty("message") String message) {
        this.repository = repository;
        this.commitSha = commitSha;
        this.runId = runId;
        this.cweId = cweId;
        this.ruleId = ruleId;
        this.fingerprint = fingerprint;
        this.filePath = filePath;
        this.startLine = startLine;
        this.confidencePercent = confidencePercent;
        this.message = message;
    }

    public String getRepository() {
        return repository;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public String getRunId() {
        return runId;
    }

    public String getCweId() {
        return cweId;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getFilePath() {
        return filePath;
    }

    public Integer getStartLine() {
        return startLine;
    }

    public Integer getConfidencePercent() {
        return confidencePercent;
    }

    public String getMessage() {
        return message;
    }
}
