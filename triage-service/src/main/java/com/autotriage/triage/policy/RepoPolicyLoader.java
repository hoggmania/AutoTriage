package com.autotriage.triage.policy;

import com.autotriage.triage.git.GitCredentialsProvider;
import com.autotriage.triage.git.GitRepositoryService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class RepoPolicyLoader {

    private static final Logger log = Logger.getLogger(RepoPolicyLoader.class);

    private final GitRepositoryService gitRepositoryService;
    private final GitCredentialsProvider credentialsProvider;

    @Inject
    public RepoPolicyLoader(GitRepositoryService gitRepositoryService, GitCredentialsProvider credentialsProvider) {
        this.gitRepositoryService = gitRepositoryService;
        this.credentialsProvider = credentialsProvider;
    }

    public String loadPolicy(String repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            return null;
        }
        if (!isAllowedRepository(repositoryUrl)) {
            log.warnv("Skipping triage policy clone for non-allowlisted repository {0}", repositoryUrl);
            return null;
        }
        CredentialsProvider provider = credentialsProvider.build();
        String policyPath = ConfigProvider.getConfig()
                .getOptionalValue("triage.policy.path", String.class)
                .orElse(".autotriage/policy.cel");
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("autotriage-triage-policy-");
            try (Git git = gitRepositoryService.cloneRepository(repositoryUrl, provider, tempDir)) {
                Path policyFile = tempDir.resolve(policyPath);
                if (!Files.exists(policyFile)) {
                    return null;
                }
                return Files.readString(policyFile, StandardCharsets.UTF_8).trim();
            }
        } catch (Exception e) {
            log.warnv("Failed to load policy from {0}: {1}", repositoryUrl, e.getMessage());
            return null;
        } finally {
            gitRepositoryService.deleteWorkspace(tempDir);
        }
    }

    private boolean isAllowedRepository(String repositoryUrl) {
        String allowlist = ConfigProvider.getConfig()
                .getOptionalValue("triage.policy.repository-allowlist", String.class)
                .orElse("");
        Set<String> allowedRepositories = Arrays.stream(allowlist.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toSet());
        return !allowedRepositories.isEmpty() && allowedRepositories.contains(repositoryUrl);
    }
}
