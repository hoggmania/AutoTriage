package com.autotriage.common.evidence;

import com.autotriage.common.model.TriageCandidateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TriageEvidenceTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void calibratedEvidenceRoundTripsWithCompleteProvenanceAndRawSignals() throws Exception {
        EvidenceProvenance provenance = new EvidenceProvenance(
                "zerofalse", "1.4.0", "openai", "gpt-4o", "2024-08-06",
                "optimized", "a".repeat(64), "b".repeat(64), "c".repeat(64),
                Instant.parse("2026-08-10T12:00:00Z"));
        EvidenceCalibration calibration = new EvidenceCalibration(
                "zerofalse-signals", "1", "conservative-v1", EvidenceLevel.MODERATE, 0.62);
        ZeroFalseSignals signals = new ZeroFalseSignals(true, "Yes", "No", "medium");
        TriageEvidence evidence = new TriageEvidence(EvidenceLevel.MODERATE, calibration, provenance, signals);
        TriageCandidateRequest request = new TriageCandidateRequest(
                "https://example.com/repo.git", "abc", "run", "CWE-079", "rule", "fp",
                "src/App.java", 12, 62, "message", evidence);

        TriageCandidateRequest restored = mapper.readValue(mapper.writeValueAsBytes(request), TriageCandidateRequest.class);

        assertEquals(EvidenceLevel.MODERATE, restored.getEvidence().getCalibratedLevel());
        assertEquals("openai", restored.getEvidence().getProvenance().getProvider());
        assertEquals("conservative-v1", restored.getEvidence().getCalibration().getProfile());
        assertEquals("No", restored.getEvidence().getRawZeroFalseSignals().getAttackFeasible());
        assertEquals(62, restored.getConfidencePercent());
    }
}
