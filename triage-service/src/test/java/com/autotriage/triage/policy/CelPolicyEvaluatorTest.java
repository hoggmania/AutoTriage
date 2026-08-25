package com.autotriage.triage.policy;

import com.autotriage.common.evidence.EvidenceCalibration;
import com.autotriage.common.evidence.EvidenceLevel;
import com.autotriage.common.evidence.EvidenceProvenance;
import com.autotriage.common.evidence.TriageEvidence;
import com.autotriage.common.evidence.ZeroFalseSignals;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CelPolicyEvaluatorTest {

    @Test
    void evaluatesStructuredEvidenceAndProvenance() {
        CelPolicyEvaluator evaluator = new CelPolicyEvaluator();
        String policy = "evidence.level == 'STRONG' && provenance.engineId == 'zerofalse' "
                + "? 'FALSE_POSITIVE' : 'TRUE_POSITIVE'";

        assertEquals("FALSE_POSITIVE", evaluator.evaluate(policy, "CWE-079", evidence(EvidenceLevel.STRONG)));
        assertEquals("TRUE_POSITIVE", evaluator.evaluate(policy, "CWE-079", evidence(EvidenceLevel.LIMITED)));
    }

    @Test
    void legacyConfidenceIsNotAvailableAsPolicyInput() {
        CelPolicyEvaluator evaluator = new CelPolicyEvaluator();

        assertNull(evaluator.evaluate(
                "confidencePercent > 0 ? 'FALSE_POSITIVE' : 'TRUE_POSITIVE'",
                "CWE-079",
                evidence(EvidenceLevel.INSUFFICIENT)));
    }

    private static TriageEvidence evidence(EvidenceLevel level) {
        return new TriageEvidence(
                level,
                new EvidenceCalibration("zerofalse-signals", "1", "conservative-v1", level, 0.99),
                new EvidenceProvenance(
                        "zerofalse", "1", "openai", "gpt-4o", "2024-08-06", "optimized",
                        "a".repeat(64), "b".repeat(64), "c".repeat(64), Instant.parse("2026-08-10T12:00:00Z")),
                new ZeroFalseSignals(true, "Yes", "No", "high"));
    }
}
