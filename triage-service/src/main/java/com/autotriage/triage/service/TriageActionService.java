package com.autotriage.triage.service;

import com.autotriage.triage.model.FindingEntity;
import com.autotriage.triage.model.FindingStatus;
import com.autotriage.triage.suppressions.SuppressionUpdateService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TriageActionService {

    private static final Logger log = Logger.getLogger(TriageActionService.class);

    private final AuditService auditService;
    private final SuppressionUpdateService suppressionUpdateService;

    @Inject
    public TriageActionService(AuditService auditService,
                               SuppressionUpdateService suppressionUpdateService) {
        this.auditService = auditService;
        this.suppressionUpdateService = suppressionUpdateService;
    }

    @Transactional
    public FindingEntity claim(UUID findingId, String actor) {
        FindingEntity finding = FindingEntity.findById(findingId);
        if (finding == null) {
            return null;
        }
        if (finding.status == FindingStatus.NEW || (finding.claimedBy != null && finding.claimedBy.equals(actor))) {
            finding.status = FindingStatus.TRIAGING;
            finding.claimedBy = actor;
            finding.claimedAt = Instant.now();
            finding.updatedAt = Instant.now();
            auditService.record(finding.repository, finding.id, "CLAIMED", actor, null);
        }
        return finding;
    }

    @Transactional
    public FindingEntity deny(UUID findingId, String actor) {
        FindingEntity finding = FindingEntity.findById(findingId);
        if (finding == null) {
            return null;
        }
        finding.status = FindingStatus.DENIED;
        finding.decidedBy = actor;
        finding.decidedAt = Instant.now();
        finding.updatedAt = Instant.now();
        auditService.record(finding.repository, finding.id, "DENIED", actor, null);
        return finding;
    }

    @Transactional
    public FindingEntity approve(UUID findingId, String actor) {
        FindingEntity finding = FindingEntity.findById(findingId);
        if (finding == null) {
            return null;
        }
        finding.status = FindingStatus.APPROVED;
        finding.decidedBy = actor;
        finding.decidedAt = Instant.now();
        finding.updatedAt = Instant.now();
        auditService.record(finding.repository, finding.id, "APPROVED", actor, null);

        Optional<SuppressionUpdateService.SuppressionPrResult> prResult = suppressionUpdateService.createSuppressionPr(finding);
        if (prResult.isPresent()) {
            finding.prBranch = prResult.get().branch();
            finding.prUrl = prResult.get().prUrl();
            finding.updatedAt = Instant.now();
            auditService.record(finding.repository, finding.id, "PR_CREATED", actor, finding.prUrl);
        } else {
            log.warnv("Suppression PR creation failed for finding {0}", finding.id);
            auditService.record(finding.repository, finding.id, "PR_FAILED", actor, null);
        }
        return finding;
    }
}
