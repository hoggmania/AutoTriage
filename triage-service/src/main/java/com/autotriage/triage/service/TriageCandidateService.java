package com.autotriage.triage.service;

import com.autotriage.common.model.TriageCandidateRequest;
import com.autotriage.common.model.TriageCandidateResponse;
import com.autotriage.common.model.TriageClassification;
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

    private final TriagePolicyService policyService;
    private final AuditService auditService;

    @Inject
    public TriageCandidateService(TriagePolicyService policyService, AuditService auditService) {
        this.policyService = policyService;
        this.auditService = auditService;
    }

    @Transactional
    public TriageCandidateResponse ingest(TriageCandidateRequest request) {
        if (request == null || request.getConfidencePercent() == null) {
            return new TriageCandidateResponse(TriageClassification.TRUE_POSITIVE, false, null);
        }
        if (request.getRepository() == null || request.getRepository().isBlank()) {
            return new TriageCandidateResponse(TriageClassification.TRUE_POSITIVE, false, null);
        }
        TriageClassification classification = policyService.classify(
                request.getRepository(),
                request.getCweId(),
                request.getConfidencePercent());
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
        finding.classification = classification;
        finding.status = FindingStatus.NEW;
        finding.createdAt = Instant.now();
        finding.updatedAt = Instant.now();
        finding.persist();
        auditService.record(finding.repository, finding.id, "CANDIDATE_STORED", null, "Potential false positive");
        return new TriageCandidateResponse(classification, true, finding.id.toString());
    }
}
