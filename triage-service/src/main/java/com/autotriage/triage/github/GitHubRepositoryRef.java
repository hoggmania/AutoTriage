package com.autotriage.triage.github;

public record GitHubRepositoryRef(
        String owner,
        String repository,
        String defaultBranch,
        String baseSha) {
}
