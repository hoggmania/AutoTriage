package com.autotriage.triage.github;

public record GitHubPullRequest(
        long number,
        String nodeId,
        String htmlUrl,
        String headSha,
        String baseBranch,
        String state) {
}
