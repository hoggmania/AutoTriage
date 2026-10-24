package com.autotriage.common.remediation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RemediationRoutingPolicyTest {
    private static UnifiedAnalysisResult result(TriageVerdict verdict, int confidence,
                                                RemediationStatus status, String patch) {
        return new UnifiedAnalysisResult("finding-1", verdict, confidence, "analysis evidence",
                status, patch, "remediation rationale", List.of("trace"));
    }

    @Test void confidentFalsePositiveGoesToReview() {
        assertEquals(RemediationDisposition.SUPPRESSION_REVIEW,
                RemediationRoutingPolicy.route(result(TriageVerdict.FALSE_POSITIVE, 95,
                        RemediationStatus.FIX_NOT_ATTEMPTED, null), 90));
    }

    @Test void uncertainWithPatchGoesToValidation() {
        assertEquals(RemediationDisposition.VALIDATE_PATCH,
                RemediationRoutingPolicy.route(result(TriageVerdict.UNCERTAIN, 40,
                        RemediationStatus.FIX_GENERATED, "s3://patches/finding-1.diff"), 90));
    }

    @Test void noAutomatedFixRequiresManualRemediation() {
        assertEquals(RemediationDisposition.MANUAL_REMEDIATION,
                RemediationRoutingPolicy.route(result(TriageVerdict.TRUE_POSITIVE, 95,
                        RemediationStatus.NO_AUTOMATED_FIX_FOUND, null), 90));
    }

    @Test void inconclusiveRequestsMoreContext() {
        assertEquals(RemediationDisposition.REQUEST_MORE_CONTEXT,
                RemediationRoutingPolicy.route(result(TriageVerdict.UNCERTAIN, 40,
                        RemediationStatus.ANALYSIS_INCONCLUSIVE, null), 90));
    }

    @Test void lowConfidenceFalsePositiveCannotBeSuppressed() {
        assertEquals(RemediationDisposition.MANUAL_REMEDIATION,
                RemediationRoutingPolicy.route(result(TriageVerdict.FALSE_POSITIVE, 45,
                        RemediationStatus.FIX_NOT_ATTEMPTED, null), 90));
    }

    @Test void patchRequiresArtifact() {
        assertThrows(IllegalArgumentException.class,
                () -> result(TriageVerdict.UNCERTAIN, 50, RemediationStatus.FIX_GENERATED, null));
    }

    @Test void confidenceMustBeInRange() {
        assertThrows(IllegalArgumentException.class,
                () -> result(TriageVerdict.UNCERTAIN, 101, RemediationStatus.FIX_NOT_ATTEMPTED, null));
    }
}
