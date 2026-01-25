package com.autotriage.triage.suppressions;

import com.autotriage.triage.git.GitCredentialsProvider;
import com.autotriage.triage.git.GitRepositoryService;
import com.autotriage.triage.model.FindingEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class SuppressionUpdateService {

    private static final Logger log = Logger.getLogger(SuppressionUpdateService.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String SIGNATURE = "TEST-SIGNATURE";

    private final GitRepositoryService gitRepositoryService;
    private final GitCredentialsProvider credentialsProvider;

    @Inject
    public SuppressionUpdateService(GitRepositoryService gitRepositoryService,
                                    GitCredentialsProvider credentialsProvider) {
        this.gitRepositoryService = gitRepositoryService;
        this.credentialsProvider = credentialsProvider;
    }

    public Optional<SuppressionPrResult> createSuppressionPr(FindingEntity finding) {
        if (finding == null || finding.repository == null || finding.repository.isBlank()) {
            return Optional.empty();
        }
        CredentialsProvider provider = credentialsProvider.build();
        Path workspace = null;
        try {
            workspace = Files.createTempDirectory("autotriage-suppressions-pr-");
            try (Git git = gitRepositoryService.cloneRepository(finding.repository, provider, workspace)) {
                String defaultBranch = gitRepositoryService.resolveCheckedOutBranch(git);
                String branchName = "autotriage/suppressions/" + finding.id;
                git.checkout()
                        .setCreateBranch(true)
                        .setName(branchName)
                        .call();
                Path suppressionsDir = workspace.resolve(".opengrep").resolve("suppressions");
                Files.createDirectories(suppressionsDir);
                Path suppressionFile = resolveSuppressionFile(suppressionsDir);
                ArrayNode entries = readSuppressions(suppressionFile);
                if (!containsFingerprint(entries, finding.fingerprint)) {
                    ObjectNode entry = mapper.createObjectNode();
                    entry.put("fingerprint", finding.fingerprint);
                    entry.put("reason", "triage");
                    entry.put("createdAt", Instant.now().toString());
                    entries.add(entry);
                }
                Files.writeString(suppressionFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(entries), StandardCharsets.UTF_8);
                Path signatureFile = Path.of(suppressionFile.toString() + ".sig");
                Files.writeString(signatureFile, SIGNATURE, StandardCharsets.UTF_8);

                git.add().addFilepattern(".opengrep/suppressions").call();
                git.commit()
                        .setMessage("chore: add suppression for " + finding.id)
                        .call();
                git.push()
                        .setCredentialsProvider(provider)
                        .setRemote("origin")
                        .add(branchName)
                        .call();

                String prUrl = buildPrUrl(finding.repository, defaultBranch, branchName);
                return Optional.of(new SuppressionPrResult(branchName, prUrl));
            }
        } catch (Exception e) {
            log.warnv("Failed to create suppression PR: {0}", e.getMessage());
            return Optional.empty();
        } finally {
            gitRepositoryService.deleteWorkspace(workspace);
        }
    }

    private Path resolveSuppressionFile(Path suppressionsDir) {
        try {
            return Files.list(suppressionsDir)
                    .filter(path -> path.toString().endsWith(".yaml") || path.toString().endsWith(".yml"))
                    .findFirst()
                    .orElse(suppressionsDir.resolve("suppressions.yaml"));
        } catch (Exception e) {
            return suppressionsDir.resolve("suppressions.yaml");
        }
    }

    private ArrayNode readSuppressions(Path file) {
        if (!Files.exists(file)) {
            return mapper.createArrayNode();
        }
        try {
            JsonNode node = mapper.readTree(file.toFile());
            if (node.isArray()) {
                return (ArrayNode) node;
            }
            if (node.isObject()) {
                ArrayNode array = mapper.createArrayNode();
                array.add(node);
                return array;
            }
        } catch (Exception ignored) {
        }
        return mapper.createArrayNode();
    }

    private boolean containsFingerprint(ArrayNode entries, String fingerprint) {
        if (fingerprint == null) {
            return false;
        }
        for (JsonNode entry : entries) {
            String existing = entry.path("fingerprint").asText(null);
            if (fingerprint.equals(existing)) {
                return true;
            }
        }
        return false;
    }

    private String buildPrUrl(String repoUrl, String baseBranch, String branchName) {
        String template = ConfigProvider.getConfig()
                .getOptionalValue("triage.pr.url-template", String.class)
                .orElse("");
        if (template.isBlank()) {
            return null;
        }
        String baseRepo = repoUrl.endsWith(".git") ? repoUrl.substring(0, repoUrl.length() - 4) : repoUrl;
        return template.replace("{repo}", baseRepo)
                .replace("{base}", baseBranch)
                .replace("{branch}", branchName);
    }

    public record SuppressionPrResult(String branch, String prUrl) {
    }
}
