package com.autotriage.triage.service;

import com.autotriage.common.model.TriageCandidateRequest;
import com.autotriage.common.model.TriageCandidateResponse;
import com.autotriage.common.model.TriageClassification;
import com.autotriage.triage.model.FindingEntity;
import com.autotriage.triage.model.FindingStatus;
import com.autotriage.triage.policy.RepoPolicyLoader;
import com.autotriage.triage.suppressions.SuppressionUpdateService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
@EnabledForJreRange(max = JRE.JAVA_21)
class TriageApprovalFlowTest {

    @Inject
    TriageCandidateService candidateService;

    @Inject
    TriageActionService actionService;

    @InjectMock
    RepoPolicyLoader policyLoader;

    @InjectMock
    SuppressionUpdateService suppressionUpdateService;

    @BeforeEach
    void setUp() {
        when(policyLoader.loadPolicy(anyString())).thenReturn("confidencePercent <= 30 ? \"TRUE_POSITIVE\" : "
                + "confidencePercent <= 60 ? \"POTENTIAL_FALSE_POSITIVE\" : "
                + "\"FALSE_POSITIVE\"");
        when(suppressionUpdateService.createSuppressionPr(any()))
                .thenReturn(Optional.of(new SuppressionUpdateService.SuppressionPrResult(
                        "triage/auto-1",
                        "https://example.com/pr/1")));
    }

    @Test
    @TestTransaction
    void approvesFindingAndStoresPrMetadata() {
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
                55,
                "Potential XSS finding");

        TriageCandidateResponse response = candidateService.ingest(request);
        assertEquals(TriageClassification.POTENTIAL_FALSE_POSITIVE, response.getClassification());
        assertTrue(response.isStored());

        UUID findingId = UUID.fromString(response.getFindingId());
        FindingEntity approved = actionService.approve(findingId, "tester");

        assertNotNull(approved);
        assertEquals(FindingStatus.APPROVED, approved.status);
        assertEquals("https://example.com/pr/1", approved.prUrl);
    }
}
