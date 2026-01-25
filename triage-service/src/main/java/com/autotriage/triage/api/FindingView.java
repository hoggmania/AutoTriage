package com.autotriage.triage.api;

import com.autotriage.common.model.TriageClassification;
import com.autotriage.triage.model.FindingEntity;
import com.autotriage.triage.model.FindingStatus;

import java.time.Instant;

public final class FindingView {
    private final String id;
    private final String repository;
    private final String commitSha;
    private final String runId;
    private final String cweId;
    private final String ruleId;
    private final String fingerprint;
    private final String filePath;
    private final Integer startLine;
    private final Integer confidencePercent;
    private final TriageClassification classification;
    private final FindingStatus status;
    private final String claimedBy;
    private final String decidedBy;
    private final String prUrl;
    private final String prBranch;
    private final Instant createdAt;
    private final Instant updatedAt;

    public FindingView(FindingEntity entity) {
        this.id = entity.id == null ? null : entity.id.toString();
        this.repository = entity.repository;
        this.commitSha = entity.commitSha;
        this.runId = entity.runId;
        this.cweId = entity.cweId;
        this.ruleId = entity.ruleId;
        this.fingerprint = entity.fingerprint;
        this.filePath = entity.filePath;
        this.startLine = entity.startLine;
        this.confidencePercent = entity.confidencePercent;
        this.classification = entity.classification;
        this.status = entity.status;
        this.claimedBy = entity.claimedBy;
        this.decidedBy = entity.decidedBy;
        this.prUrl = entity.prUrl;
        this.prBranch = entity.prBranch;
        this.createdAt = entity.createdAt;
        this.updatedAt = entity.updatedAt;
    }

    public String getId() {
        return id;
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

    public TriageClassification getClassification() {
        return classification;
    }

    public FindingStatus getStatus() {
        return status;
    }

    public String getClaimedBy() {
        return claimedBy;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public String getPrUrl() {
        return prUrl;
    }

    public String getPrBranch() {
        return prBranch;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
