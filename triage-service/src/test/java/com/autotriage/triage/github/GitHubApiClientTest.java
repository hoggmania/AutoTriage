package com.autotriage.triage.github;

import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubApiClientTest {

    @Test
    void createsBranchUpdatesContentAndCreatesRealPullRequest() throws Exception {
        FakeTransport transport = new FakeTransport(
                response(200, "{\"id\":42}"),
                response(201, "{\"token\":\"inst-token\",\"expires_at\":\"2026-08-10T13:00:00Z\"}"),
                response(200, "{\"default_branch\":\"main\"}"),
                response(200, "{\"object\":{\"sha\":\"base-sha\"}}"),
                response(404, "{\"message\":\"Not Found\"}"),
                response(201, "{\"object\":{\"sha\":\"base-sha\"}}"),
                response(404, "{\"message\":\"Not Found\"}"),
                response(201, "{\"content\":{\"sha\":\"blob-sha\"},\"commit\":{\"sha\":\"commit-sha\"}}"),
                response(200, "[]"),
                response(201, "{\"number\":17,\"node_id\":\"PR_node\",\"html_url\":\"https://github.com/acme/widget/pull/17\",\"state\":\"open\",\"head\":{\"sha\":\"commit-sha\"},\"base\":{\"ref\":\"main\"}}"));
        GitHubAppAuthenticator authenticator = new GitHubAppAuthenticator(1L,
                KeyPairGenerator.getInstance("RSA").generateKeyPair().getPrivate(), transport,
                Clock.fixed(Instant.parse("2026-08-10T12:00:00Z"), ZoneOffset.UTC), 10);
        GitHubApiClient client = new GitHubApiClient(authenticator, transport);

        GitHubRepositoryRef repository = client.resolveRepository("https://github.com/acme/widget.git");
        client.ensureBranch(repository, "autotriage/suppressions/id-1");
        Optional<GitHubApiClient.RepositoryContent> absent = client.readContent(
                repository, ".opengrep/suppressions/suppressions.yaml", "autotriage/suppressions/id-1");
        GitHubApiClient.ContentUpdate update = client.updateContent(repository,
                ".opengrep/suppressions/suppressions.yaml", "autotriage/suppressions/id-1",
                "chore: update", "[]", absent.map(GitHubApiClient.RepositoryContent::sha).orElse(null));
        GitHubPullRequest pullRequest = client.createOrReusePullRequest(repository,
                "autotriage/suppressions/id-1", "title", "body");

        assertEquals("main", repository.defaultBranch());
        assertEquals("base-sha", repository.baseSha());
        assertTrue(absent.isEmpty());
        assertEquals("commit-sha", update.commitSha());
        assertEquals(17, pullRequest.number());
        assertEquals("https://github.com/acme/widget/pull/17", pullRequest.htmlUrl());
        assertTrue(transport.requests.stream().allMatch(request ->
                !request.headers().toString().contains("installation-secret")));
        assertTrue(transport.requests.stream().filter(r -> r.path().startsWith("/repos/") &&
                !r.path().endsWith("/installation")).allMatch(r ->
                "Bearer inst-token".equals(r.headers().get("Authorization"))));
        assertFalse(transport.requests.stream().anyMatch(r -> r.path().contains("inst-token")));
    }

    @Test
    void reusesExistingBranchAndOpenPullRequest() throws Exception {
        FakeTransport transport = new FakeTransport(
                response(200, "{\"id\":42}"),
                response(201, "{\"token\":\"inst-token\",\"expires_at\":\"2026-08-10T13:00:00Z\"}"),
                response(200, "{\"default_branch\":\"main\"}"),
                response(200, "{\"object\":{\"sha\":\"base-sha\"}}"),
                response(200, "{\"object\":{\"sha\":\"branch-sha\"}}"),
                response(200, "[{\"number\":17,\"node_id\":\"PR_node\",\"html_url\":\"https://github.com/acme/widget/pull/17\",\"state\":\"open\",\"head\":{\"sha\":\"branch-sha\"},\"base\":{\"ref\":\"main\"}}]"));
        GitHubAppAuthenticator authenticator = new GitHubAppAuthenticator(1L,
                KeyPairGenerator.getInstance("RSA").generateKeyPair().getPrivate(), transport,
                Clock.fixed(Instant.parse("2026-08-10T12:00:00Z"), ZoneOffset.UTC), 10);
        GitHubApiClient client = new GitHubApiClient(authenticator, transport);

        GitHubRepositoryRef repository = client.resolveRepository("git@github.com:acme/widget.git");
        client.ensureBranch(repository, "autotriage/suppressions/id-1");
        GitHubPullRequest pullRequest = client.createOrReusePullRequest(repository,
                "autotriage/suppressions/id-1", "ignored", "ignored");

        assertEquals(17, pullRequest.number());
        assertFalse(transport.requests.stream().anyMatch(r ->
                "POST".equals(r.method()) && r.path().endsWith("/git/refs")));
        assertFalse(transport.requests.stream().anyMatch(r ->
                "POST".equals(r.method()) && r.path().endsWith("/pulls")));
    }

    private static GitHubTransport.Response response(int status, String body) {
        return new GitHubTransport.Response(status, Map.of(), body);
    }

    private static final class FakeTransport implements GitHubTransport {
        private final ArrayDeque<Response> responses;
        private final List<Request> requests = new ArrayList<>();

        private FakeTransport(Response... responses) {
            this.responses = new ArrayDeque<>(List.of(responses));
        }

        @Override
        public Response execute(Request request) {
            requests.add(request);
            return responses.removeFirst();
        }
    }
}
