package com.autotriage.common.remediation;

import java.util.Objects;

/** Deterministic routing after LLM analysis; never auto-suppresses or auto-merges. */
public final class RemediationRoutingPolicy {
    private RemediationRoutingPolicy() {}

    public static RemediationDisposition route(UnifiedAnalysisResult result, int falsePositiveThreshold) {
        Objects.requireNonNull(result, "result");
        if (falsePositiveThreshold < 0 || falsePositiveThreshold > 100) {
            throw new IllegalArgumentException("falsePositiveThreshold must be 0..100");
        }
        if (result.triageVerdict() == TriageVerdict.FALSE_POSITIVE
                && result.confidencePercent() >= falsePositiveThreshold) {
            return RemediationDisposition.SUPPRESSION_REVIEW;
        }
        return switch (result.remediationStatus()) {
            case FIX_GENERATED -> RemediationDisposition.VALIDATE_PATCH;
            case ANALYSIS_INCONCLUSIVE -> RemediationDisposition.REQUEST_MORE_CONTEXT;
            case NO_AUTOMATED_FIX_FOUND, FIX_NOT_ATTEMPTED -> RemediationDisposition.MANUAL_REMEDIATION;
        };
    }
}
