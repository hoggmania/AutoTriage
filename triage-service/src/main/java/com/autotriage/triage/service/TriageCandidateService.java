package com.autotriage.triage.service;

import com.autotriage.common.model.TriageCandidateRequest;
import com.autotriage.common.model.TriageCandidateResponse;
import com.autotriage.common.model.TriageClassification;
import com.autotriage.common.evidence.TriageEvidence;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.autotriage.triage.model.FindingEntity;
import com.autotriage.triage.model.FindingStatus;
import com.autotriage.triage.policy.TriagePolicyService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class TriageCandidateService {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private final TriagePolicyService policyService;
    private final AuditService auditService;

    @Inject
    public TriageCandidateService(TriagePolicyService policyService, AuditService auditService) {
        this.policyService = policyService;
        this.auditService = auditService;
    }

    @Transactional
    public TriageCandidateResponse ingest(TriageCandidateRequest request) {
        if (request == null) {
            return new TriageCandidateResponse(TriageClassification.TRUE_POSITIVE, false, null);
        }
        if (request.getRepository() == null || request.getRepository().isBlank()) {
            return new TriageCandidateResponse(TriageClassification.TRUE_POSITIVE, false, null);
        }
        TriageClassification classification = policyService.classify(
                request.getRepository(),
                request.getCweId(), request.getEvidence());
        if (classification != TriageClassification.POTENTIAL_FALSE_POSITIVE) {
            return new TriageCandidateResponse(classification, false, null);
        }
        FindingEntity existing = FindingEntity.find(
                "repository = ?1 and commitSha = ?2 and fingerprint = ?3",
                request.getRepository(),
                request.getCommitSha(),
                request.getFingerprint())
                .firstResult();
        if (existing != null) {
            existing.classification = classification;
            copyEvidence(existing, request.getEvidence());
            existing.updatedAt = Instant.now();
            return new TriageCandidateResponse(classification, true, existing.id.toString());
        }
        FindingEntity finding = new FindingEntity();
        finding.id = UUID.randomUUID();
        finding.repository = request.getRepository();
        finding.commitSha = request.getCommitSha();
        finding.runId = request.getRunId();
        finding.cweId = request.getCweId();
        finding.ruleId = request.getRuleId();
        finding.fingerprint = request.getFingerprint();
        finding.filePath = request.getFilePath();
        finding.startLine = request.getStartLine();
        finding.confidencePercent = request.getConfidencePercent();
        copyEvidence(finding, request.getEvidence());
        finding.classification = classification;
        finding.status = FindingStatus.NEW;
        finding.createdAt = Instant.now();
        finding.updatedAt = Instant.now();
        finding.persist();
        auditService.record(finding.repository, finding.id, "CANDIDATE_STORED", null, "Potential false positive");
        return new TriageCandidateResponse(classification, true, finding.id.toString());
    }

    private static void copyEvidence(FindingEntity finding, TriageEvidence evidence) {
        if (evidence == null) return;
        var provenance = evidence.getProvenance();
        var calibration = evidence.getCalibration();
        var signals = evidence.getRawZeroFalseSignals();
        finding.evidenceLevel = evidence.getCalibratedLevel();
        finding.engineId = provenance.getEngineId();
        finding.engineVersion = provenance.getEngineVersion();
        finding.provider = provenance.getProvider();
        finding.model = provenance.getModel();
        finding.modelVersion = provenance.getModelVersion();
        finding.promptVariant = provenance.getPromptVariant();
        finding.promptSha256 = provenance.getPromptHash();
        finding.sourceSha256 = provenance.getSourceHash();
        finding.sarifSha256 = provenance.getSarifHash();
        finding.evaluatedAt = provenance.getEvaluatedAt();
        finding.calibratorId = calibration.getCalibratorId();
        finding.calibrationVersion = calibration.getVersion();
        finding.calibrationProfile = calibration.getProfile();
        finding.calibrationRawScore = calibration.getRawScore();
        finding.rawFalsePositive = signals.getFalsePositive();
        finding.rawSanitizationFound = signals.getSanitizationFound();
        finding.rawAttackFeasible = signals.getAttackFeasible();
        finding.rawEvidenceConfidence = signals.getConfidence();
        try {
            finding.evidenceJson = MAPPER.writeValueAsString(evidence);
        } catch (Exception e) {
            throw new IllegalArgumentException("Evidence cannot be serialized", e);
        }
    }
}
