package com.autotriage.triage.service;

import com.autotriage.common.evidence.EvidenceCalibration;
import com.autotriage.common.evidence.EvidenceLevel;
import com.autotriage.common.evidence.EvidenceProvenance;
import com.autotriage.common.evidence.TriageEvidence;
import com.autotriage.common.evidence.ZeroFalseSignals;
import com.autotriage.common.model.TriageCandidateRequest;
import com.autotriage.common.model.TriageCandidateResponse;
import com.autotriage.common.model.TriageClassification;
import com.autotriage.triage.model.FindingEntity;
import com.autotriage.triage.policy.RepoPolicyLoader;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
class TriageCandidateServiceTest {

    @Inject TriageCandidateService candidateService;
    @InjectMock RepoPolicyLoader policyLoader;

    @BeforeEach
    void setUp() {
        when(policyLoader.loadPolicy(anyString())).thenReturn(
                "evidence.level == 'STRONG' ? 'FALSE_POSITIVE' : "
                        + "evidence.level == 'MODERATE' ? 'POTENTIAL_FALSE_POSITIVE' : 'TRUE_POSITIVE'");
    }

    @Test
    @TestTransaction
    void storesModerateEvidenceWithFullProvenance() {
        FindingEntity.deleteAll();
        TriageEvidence evidence = evidence(EvidenceLevel.MODERATE);
        TriageCandidateRequest request = request("provenance-fp", 50, evidence);

        TriageCandidateResponse response = candidateService.ingest(request);

        assertEquals(TriageClassification.POTENTIAL_FALSE_POSITIVE, response.getClassification());
        assertTrue(response.isStored());
        FindingEntity stored = FindingEntity.findById(UUID.fromString(response.getFindingId()));
        assertNotNull(stored);
        assertEquals(EvidenceLevel.MODERATE, stored.evidenceLevel);
        assertEquals("zerofalse", stored.engineId);
        assertEquals("gpt-4o", stored.model);
        assertEquals("optimized", stored.promptVariant);
        assertEquals("b".repeat(64), stored.sourceSha256);
        assertEquals("c".repeat(64), stored.sarifSha256);
        assertEquals("conservative-v1", stored.calibrationProfile);
        assertEquals("No", stored.rawAttackFeasible);
        assertEquals(Instant.parse("2026-08-10T12:00:00Z"), stored.evaluatedAt);
    }

    @Test
    @TestTransaction
    void confidenceAloneCannotElevateMissingEvidence() {
        FindingEntity.deleteAll();

        TriageCandidateResponse response = candidateService.ingest(request("legacy-only", 99, null));

        assertEquals(TriageClassification.TRUE_POSITIVE, response.getClassification());
        assertFalse(response.isStored());
        assertEquals(0, FindingEntity.count());
    }

    private static TriageCandidateRequest request(String fingerprint, int confidence, TriageEvidence evidence) {
        return new TriageCandidateRequest(
                "https://example.com/repo.git", "abc123", "run-1", "CWE-079", "rule-1", fingerprint,
                "src/App.java", 42, confidence, "Potential XSS finding", evidence);
    }

    private static TriageEvidence evidence(EvidenceLevel level) {
        return new TriageEvidence(
                level,
                new EvidenceCalibration("zerofalse-signals", "1", "conservative-v1", level, 0.50),
                new EvidenceProvenance(
                        "zerofalse", "1.0", "openai", "gpt-4o", "2024-08-06", "optimized",
                        "a".repeat(64), "b".repeat(64), "c".repeat(64), Instant.parse("2026-08-10T12:00:00Z")),
                new ZeroFalseSignals(true, "Yes", "No", "medium"));
    }
}
