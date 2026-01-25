package com.autotriage.triage.service;

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
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
@EnabledForJreRange(max = JRE.JAVA_21)
class TriageCandidateServiceTest {

    @Inject
    TriageCandidateService candidateService;

    @InjectMock
    RepoPolicyLoader policyLoader;

    @BeforeEach
    void setUp() {
        when(policyLoader.loadPolicy(anyString())).thenReturn("confidencePercent <= 30 ? \"TRUE_POSITIVE\" : "
                + "confidencePercent <= 60 ? \"POTENTIAL_FALSE_POSITIVE\" : "
                + "\"FALSE_POSITIVE\"");
    }

    @Test
    @TestTransaction
    void storesPotentialFalsePositiveCandidate() {
        FindingEntity.deleteAll();

        TriageCandidateRequest request = new TriageCandidateRequest(
                "https://example.com/repo.git",
                "abc123",
                "run-1",
                "CWE-079",
                "rule-1",
                "fingerprint-1",
                "src/App.java",
                42,
                50,
                "Potential XSS finding");

        TriageCandidateResponse response = candidateService.ingest(request);

        assertEquals(TriageClassification.POTENTIAL_FALSE_POSITIVE, response.getClassification());
        assertTrue(response.isStored());
        assertNotNull(response.getFindingId());

        FindingEntity stored = FindingEntity.findById(UUID.fromString(response.getFindingId()));
        assertNotNull(stored);
        assertEquals("https://example.com/repo.git", stored.repository);
    }
}
