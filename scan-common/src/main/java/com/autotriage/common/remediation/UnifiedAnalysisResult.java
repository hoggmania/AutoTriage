package com.autotriage.common.remediation;

import java.util.List;
import java.util.Objects;

/** Immutable result of a unified triage and remediation model invocation. */
public record UnifiedAnalysisResult(
        String findingId,
        TriageVerdict triageVerdict,
        int confidencePercent,
        String explanation,
        RemediationStatus remediationStatus,
        String patchArtifactUri,
        String remediationExplanation,
        List<String> evidence) {

    public UnifiedAnalysisResult {
        if (findingId == null || findingId.isBlank()) throw new IllegalArgumentException("findingId is required");
        Objects.requireNonNull(triageVerdict, "triageVerdict");
        if (confidencePercent < 0 || confidencePercent > 100) throw new IllegalArgumentException("confidencePercent must be 0..100");
        if (explanation == null || explanation.isBlank()) throw new IllegalArgumentException("explanation is required");
        Objects.requireNonNull(remediationStatus, "remediationStatus");
        if (remediationExplanation == null || remediationExplanation.isBlank()) throw new IllegalArgumentException("remediationExplanation is required");
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        if (remediationStatus == RemediationStatus.FIX_GENERATED && (patchArtifactUri == null || patchArtifactUri.isBlank())) {
            throw new IllegalArgumentException("FIX_GENERATED requires patchArtifactUri");
        }
        if (remediationStatus != RemediationStatus.FIX_GENERATED && patchArtifactUri != null) {
            throw new IllegalArgumentException("patchArtifactUri is only valid for FIX_GENERATED");
        }
        if (triageVerdict == TriageVerdict.FALSE_POSITIVE && remediationStatus == RemediationStatus.FIX_GENERATED) {
            throw new IllegalArgumentException("FALSE_POSITIVE cannot generate a patch");
        }
    }
}
