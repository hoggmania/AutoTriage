package com.autotriage.triage.suppressions;

import com.autotriage.common.identity.SignatureEnvelopeJson;
import com.autotriage.common.identity.SigningIdentity;
import com.autotriage.identity.kms.KmsSigningIdentity;
import com.autotriage.triage.github.GitHubApiClient;
import com.autotriage.triage.github.GitHubPullRequest;
import com.autotriage.triage.github.GitHubRepositoryRef;
import com.autotriage.triage.model.FindingEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Creates suppression changes and real pull requests through the GitHub App REST API. */
@ApplicationScoped
public class SuppressionUpdateService {
    private static final Logger log = Logger.getLogger(SuppressionUpdateService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ObjectMapper CANONICAL_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private static final String SUPPRESSION_PATH = ".opengrep/suppressions/suppressions.yaml";
    public static final String KMS_KEY_ARN_CONFIG = "suppression.signature.kms-key-arn";

    private final GitHubApiClient gitHub;
    private final Supplier<SigningIdentity> signingIdentity;

    @Inject
    public SuppressionUpdateService(GitHubApiClient gitHub) {
        this.gitHub = java.util.Objects.requireNonNull(gitHub, "gitHub");
        this.signingIdentity = SuppressionUpdateService::configuredSigningIdentity;
    }

    SuppressionUpdateService(GitHubApiClient gitHub, SigningIdentity signingIdentity) {
        this.gitHub = gitHub;
        this.signingIdentity = () -> java.util.Objects.requireNonNull(signingIdentity, "signingIdentity");
    }

    public Optional<SuppressionPrResult> createSuppressionPr(FindingEntity finding) {
        if (finding == null || finding.repository == null || finding.repository.isBlank()
                || finding.id == null || finding.fingerprint == null || finding.fingerprint.isBlank()) {
            return Optional.empty();
        }
        try {
            GitHubRepositoryRef repository = gitHub.resolveRepository(finding.repository);
            String branch = "autotriage/suppressions/" + finding.id;
            gitHub.ensureBranch(repository, branch);

            Optional<GitHubApiClient.RepositoryContent> current = gitHub.readContent(repository, SUPPRESSION_PATH, branch);
            ArrayNode entries = readSuppressions(current.map(GitHubApiClient.RepositoryContent::content).orElse(""));
            if (!containsFingerprint(entries, finding.fingerprint)) {
                ObjectNode entry = MAPPER.createObjectNode();
                entry.put("fingerprint", finding.fingerprint);
                entry.put("reason", "triage");
                entry.put("createdAt", Instant.now().toString());
                entries.add(entry);
            }

            SignedSuppressions signed = signSuppressions(entries);
            byte[] canonicalPayload = signed.payload();
            gitHub.updateContent(repository, SUPPRESSION_PATH, branch,
                    "chore: add suppression for " + finding.id,
                    new String(canonicalPayload, StandardCharsets.UTF_8),
                    current.map(GitHubApiClient.RepositoryContent::sha).orElse(null));

            String signaturePath = SUPPRESSION_PATH + ".sig";
            Optional<GitHubApiClient.RepositoryContent> currentSignature =
                    gitHub.readContent(repository, signaturePath, branch);
            String signatureJson = new String(signed.signatureEnvelope(),
                    StandardCharsets.UTF_8);
            gitHub.updateContent(repository, signaturePath, branch,
                    "chore: sign suppression update for " + finding.id, signatureJson,
                    currentSignature.map(GitHubApiClient.RepositoryContent::sha).orElse(null));

            GitHubPullRequest pullRequest = gitHub.createOrReusePullRequest(repository, branch,
                    "chore: add AutoTriage suppression for " + finding.id,
                    "Adds a signed suppression approved through AutoTriage triage.");
            return Optional.of(new SuppressionPrResult(branch, pullRequest.number(), pullRequest.htmlUrl()));
        } catch (Exception e) {
            log.warnv("Failed to create suppression PR through GitHub App API: {0}", e.getMessage());
            return Optional.empty();
        }
    }

    private ArrayNode readSuppressions(String content) {
        if (content == null || content.isBlank()) {
            return MAPPER.createArrayNode();
        }
        try {
            JsonNode node = MAPPER.readTree(content);
            if (node.isArray()) return (ArrayNode) node;
            if (node.isObject()) return MAPPER.createArrayNode().add(node);
        } catch (Exception ignored) {
            // Fail closed below rather than silently discarding malformed repository policy.
            throw new IllegalArgumentException("Existing suppression file is malformed", ignored);
        }
        throw new IllegalArgumentException("Existing suppression file must be an array or object");
    }

    private boolean containsFingerprint(ArrayNode entries, String fingerprint) {
        for (JsonNode entry : entries) {
            if (fingerprint.equals(entry.path("fingerprint").asText(null))) return true;
        }
        return false;
    }

    SignedSuppressions signSuppressions(ArrayNode entries) {
        try {
            byte[] canonical = CANONICAL_MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(MAPPER.convertValue(entries, Object.class));
            return new SignedSuppressions(canonical,
                    SignatureEnvelopeJson.write(signingIdentity.get().sign(canonical)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign suppression content", e);
        }
    }

    private static SigningIdentity configuredSigningIdentity() {
        String keyArn = ConfigProvider.getConfig().getOptionalValue(KMS_KEY_ARN_CONFIG, String.class)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalStateException(KMS_KEY_ARN_CONFIG + " is required"));
        return KmsSigningIdentity.fromDefaultCredentials(keyArn, Map.of("service", "triage-service"));
    }

    public record SuppressionPrResult(String branch, long prNumber, String prUrl) {}
    record SignedSuppressions(byte[] payload, byte[] signatureEnvelope) {}
}
