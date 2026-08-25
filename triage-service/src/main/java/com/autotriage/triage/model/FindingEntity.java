package com.autotriage.triage.model;

import com.autotriage.common.model.TriageClassification;
import com.autotriage.common.evidence.EvidenceLevel;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "triage_findings")
public class FindingEntity extends PanacheEntityBase {
    @Id
    public UUID id;

    @Column(name = "repository", nullable = false)
    public String repository;

    @Column(name = "commit_sha")
    public String commitSha;

    @Column(name = "run_id")
    public String runId;

    @Column(name = "cwe_id")
    public String cweId;

    @Column(name = "rule_id")
    public String ruleId;

    @Column(name = "fingerprint")
    public String fingerprint;

    @Column(name = "file_path")
    public String filePath;

    @Column(name = "start_line")
    public Integer startLine;

    @Column(name = "confidence_percent")
    public Integer confidencePercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_level")
    public EvidenceLevel evidenceLevel;
    @Column(name = "engine_id") public String engineId;
    @Column(name = "engine_version") public String engineVersion;
    @Column(name = "evidence_provider") public String provider;
    @Column(name = "evidence_model") public String model;
    @Column(name = "model_version") public String modelVersion;
    @Column(name = "prompt_variant") public String promptVariant;
    @Column(name = "prompt_sha256", length = 64) public String promptSha256;
    @Column(name = "source_sha256", length = 64) public String sourceSha256;
    @Column(name = "sarif_sha256", length = 64) public String sarifSha256;
    @Column(name = "calibrator_id") public String calibratorId;
    @Column(name = "calibration_version") public String calibrationVersion;
    @Column(name = "calibration_profile") public String calibrationProfile;
    @Column(name = "calibration_raw_score") public Double calibrationRawScore;
    @Column(name = "evaluated_at") public Instant evaluatedAt;
    @Column(name = "raw_false_positive") public Boolean rawFalsePositive;
    @Column(name = "raw_sanitization_found") public String rawSanitizationFound;
    @Column(name = "raw_attack_feasible") public String rawAttackFeasible;
    @Column(name = "raw_evidence_confidence") public String rawEvidenceConfidence;
    @Column(name = "evidence_json", columnDefinition = "text") public String evidenceJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification")
    public TriageClassification classification;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    public FindingStatus status;

    @Column(name = "claimed_by")
    public String claimedBy;

    @Column(name = "claimed_at")
    public Instant claimedAt;

    @Column(name = "decided_by")
    public String decidedBy;

    @Column(name = "decided_at")
    public Instant decidedAt;

    @Column(name = "pr_url")
    public String prUrl;

    @Column(name = "pr_branch")
    public String prBranch;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
