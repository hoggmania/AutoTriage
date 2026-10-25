package com.autotriage.triage.suppressions;

import com.autotriage.common.identity.SignatureEnvelope;
import com.autotriage.common.identity.SignatureEnvelopeJson;
import com.autotriage.common.identity.SignatureVerifier;
import com.autotriage.common.identity.SigningIdentity;
import com.autotriage.triage.github.GitHubApiClient;
import com.autotriage.triage.github.GitHubPullRequest;
import com.autotriage.triage.github.GitHubRepositoryRef;
import com.autotriage.triage.model.FindingEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression tests for the suppression signing oracle: entries already on the branch are re-signed with the
 * service key, so they must carry a valid signature before the service will touch them.
 */
class SuppressionUpdateServiceTrustTest {
    private static final String KEY_ARN = "arn:aws:kms:us-east-1:123456789012:key/test";
    private static final String PATH = ".opengrep/suppressions/suppressions.yaml";
    private static final String SIG_PATH = PATH + ".sig";
    private static final String BRANCH_PREFIX = "autotriage/suppressions/";
    private static final String ATTACKER_ENTRIES = "[{\"fingerprint\":\"attacker-fp\",\"reason\":\"sneaky\"}]";

    private GitHubApiClient gitHub;
    private SignatureVerifier verifier;
    private SuppressionUpdateService service;
    private GitHubRepositoryRef repository;
    private FindingEntity finding;

    @BeforeEach
    void setUp() throws Exception {
        gitHub = mock(GitHubApiClient.class);
        verifier = mock(SignatureVerifier.class);
        SigningIdentity signer = payload -> envelope();
        service = new SuppressionUpdateService(gitHub, signer, verifier);

        repository = new GitHubRepositoryRef("example", "repo", "main", "base-sha");
        when(gitHub.resolveRepository(anyString())).thenReturn(repository);
        when(gitHub.createOrReusePullRequest(any(), anyString(), anyString(), anyString()))
                .thenReturn(new GitHubPullRequest(7, "node", "https://github.com/example/repo/pull/7",
                        "head", "main", "open"));

        finding = new FindingEntity();
        finding.id = UUID.randomUUID();
        finding.repository = "https://github.com/example/repo.git";
        finding.fingerprint = "fp-approved";
    }

    @Test
    void refusesToReSignUnsignedExistingSuppressions() throws Exception {
        givenContent(PATH, ATTACKER_ENTRIES);
        givenContent(SIG_PATH, null);

        Optional<SuppressionUpdateService.SuppressionPrResult> result = service.createSuppressionPr(finding);

        assertTrue(result.isEmpty());
        verify(gitHub, never()).updateContent(any(), anyString(), anyString(), anyString(), anyString(), any());
        verify(gitHub, never()).createOrReusePullRequest(any(), anyString(), anyString(), anyString());
    }

    @Test
    void refusesToReSignExistingSuppressionsWithInvalidSignature() throws Exception {
        givenContent(PATH, ATTACKER_ENTRIES);
        givenContent(SIG_PATH, new String(SignatureEnvelopeJson.write(envelope()), StandardCharsets.UTF_8));
        when(verifier.verify(any(), any())).thenReturn(false);

        Optional<SuppressionUpdateService.SuppressionPrResult> result = service.createSuppressionPr(finding);

        assertTrue(result.isEmpty());
        verify(verifier).verify(eq(ATTACKER_ENTRIES.getBytes(StandardCharsets.UTF_8)), any());
        verify(gitHub, never()).updateContent(any(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void refusesWhenExistingSignatureIsMalformed() throws Exception {
        givenContent(PATH, ATTACKER_ENTRIES);
        givenContent(SIG_PATH, "not json");

        Optional<SuppressionUpdateService.SuppressionPrResult> result = service.createSuppressionPr(finding);

        assertTrue(result.isEmpty());
        verify(gitHub, never()).updateContent(any(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void extendsExistingSuppressionsWhenSignatureIsValid() throws Exception {
        String trusted = "[{\"fingerprint\":\"fp-old\",\"reason\":\"triage\"}]";
        givenContent(PATH, trusted);
        givenContent(SIG_PATH, new String(SignatureEnvelopeJson.write(envelope()), StandardCharsets.UTF_8));
        when(verifier.verify(any(), any())).thenReturn(true);

        Optional<SuppressionUpdateService.SuppressionPrResult> result = service.createSuppressionPr(finding);

        assertTrue(result.isPresent());
        verify(gitHub).updateContent(eq(repository), eq(PATH), anyString(), anyString(), anyString(), eq("sha-" + PATH));
        verify(gitHub).updateContent(eq(repository), eq(SIG_PATH), anyString(), anyString(), anyString(),
                eq("sha-" + SIG_PATH));
    }

    @Test
    void createsFirstSuppressionFileWithoutNeedingAnExistingSignature() throws Exception {
        givenContent(PATH, null);
        givenContent(SIG_PATH, null);

        Optional<SuppressionUpdateService.SuppressionPrResult> result = service.createSuppressionPr(finding);

        assertTrue(result.isPresent());
        verify(verifier, never()).verify(any(), any());
        verify(gitHub).updateContent(eq(repository), eq(PATH), anyString(), anyString(), anyString(), isNull());
    }

    private void givenContent(String path, String content) throws Exception {
        Optional<GitHubApiClient.RepositoryContent> value = content == null
                ? Optional.empty()
                : Optional.of(new GitHubApiClient.RepositoryContent("sha-" + path, content));
        when(gitHub.readContent(eq(repository), eq(path), eq(BRANCH_PREFIX + finding.id))).thenReturn(value);
    }

    private static SignatureEnvelope envelope() {
        return new SignatureEnvelope(SignatureEnvelope.CURRENT_VERSION, KEY_ARN,
                SignatureEnvelope.KMS_PSS_SHA_256, "00".repeat(32),
                Base64.getEncoder().encodeToString("signature".getBytes(StandardCharsets.UTF_8)),
                Instant.parse("2026-08-10T12:00:00Z"), Map.of("service", "test"));
    }
}
