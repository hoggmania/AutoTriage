package com.autotriage.triage.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/** GitHub REST client authenticated exclusively as a GitHub App installation. */
@ApplicationScoped
public class GitHubApiClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GitHubAppAuthenticator authenticator;
    private final GitHubTransport transport;

    @Inject
    public GitHubApiClient(GitHubAppAuthenticator authenticator, GitHubTransport transport) {
        this.authenticator = java.util.Objects.requireNonNull(authenticator, "authenticator");
        this.transport = java.util.Objects.requireNonNull(transport, "transport");
    }

    public GitHubRepositoryRef resolveRepository(String repositoryUrl) throws Exception {
        String[] repository = parseRepository(repositoryUrl);
        String owner = repository[0];
        String name = repository[1];
        GitHubTransport.Response metadata = execute(owner, name, "GET", repoPath(owner, name), "");
        requireStatus(metadata, 200, "resolve repository");
        String defaultBranch = requiredText(MAPPER.readTree(metadata.body()), "default_branch");
        GitHubTransport.Response ref = execute(owner, name, "GET",
                repoPath(owner, name) + "/git/ref/heads/" + encode(defaultBranch), "");
        requireStatus(ref, 200, "resolve default branch");
        String baseSha = refJson(ref).path("object").path("sha").asText(null);
        if (baseSha == null || baseSha.isBlank()) {
            throw new IllegalStateException("GitHub default branch response omitted object.sha");
        }
        return new GitHubRepositoryRef(owner, name, defaultBranch, baseSha);
    }

    public void ensureBranch(GitHubRepositoryRef repository, String branch) throws Exception {
        String refPath = repoPath(repository) + "/git/ref/heads/" + encode(branch);
        GitHubTransport.Response existing = execute(repository, "GET", refPath, "");
        if (existing.statusCode() == 200) {
            return;
        }
        if (existing.statusCode() != 404) {
            throw apiFailure(existing, "check branch");
        }
        ObjectNode body = MAPPER.createObjectNode()
                .put("ref", "refs/heads/" + branch)
                .put("sha", repository.baseSha());
        GitHubTransport.Response created = execute(repository, "POST", repoPath(repository) + "/git/refs",
                MAPPER.writeValueAsString(body));
        requireStatus(created, 201, "create branch");
    }

    public Optional<RepositoryContent> readContent(GitHubRepositoryRef repository, String path, String ref)
            throws Exception {
        String endpoint = repoPath(repository) + "/contents/" + encodePath(path) + "?ref=" + encode(ref);
        GitHubTransport.Response response = execute(repository, "GET", endpoint, "");
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        requireStatus(response, 200, "read repository content");
        JsonNode json = refJson(response);
        String sha = requiredText(json, "sha");
        String encoded = json.path("content").asText("").replaceAll("\\s", "");
        String content = encoded.isEmpty() ? "" : new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        return Optional.of(new RepositoryContent(sha, content));
    }

    public ContentUpdate updateContent(GitHubRepositoryRef repository, String path, String branch,
                                       String message, String content, String existingSha) throws Exception {
        ObjectNode body = MAPPER.createObjectNode()
                .put("message", message)
                .put("content", Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)))
                .put("branch", branch);
        if (existingSha != null && !existingSha.isBlank()) {
            body.put("sha", existingSha);
        }
        GitHubTransport.Response response = execute(repository, "PUT",
                repoPath(repository) + "/contents/" + encodePath(path), MAPPER.writeValueAsString(body));
        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw apiFailure(response, "update repository content");
        }
        JsonNode json = refJson(response);
        return new ContentUpdate(json.path("content").path("sha").asText(null),
                json.path("commit").path("sha").asText(null));
    }

    public GitHubPullRequest createOrReusePullRequest(GitHubRepositoryRef repository, String branch,
                                                       String title, String body) throws Exception {
        String query = "?state=open&head=" + encode(repository.owner() + ":" + branch)
                + "&base=" + encode(repository.defaultBranch());
        GitHubTransport.Response existing = execute(repository, "GET", repoPath(repository) + "/pulls" + query, "");
        requireStatus(existing, 200, "search pull requests");
        JsonNode list = refJson(existing);
        if (list.isArray() && !list.isEmpty()) {
            return pullRequest(list.get(0));
        }
        ObjectNode request = MAPPER.createObjectNode()
                .put("title", title)
                .put("head", branch)
                .put("base", repository.defaultBranch())
                .put("body", body);
        GitHubTransport.Response created = execute(repository, "POST", repoPath(repository) + "/pulls",
                MAPPER.writeValueAsString(request));
        requireStatus(created, 201, "create pull request");
        return pullRequest(refJson(created));
    }

    private GitHubTransport.Response execute(GitHubRepositoryRef repository, String method, String path, String body)
            throws Exception {
        return execute(repository.owner(), repository.repository(), method, path, body);
    }

    private GitHubTransport.Response execute(String owner, String repository, String method, String path, String body)
            throws Exception {
        String token = authenticator.installationToken(owner, repository);
        GitHubTransport.Request request = new GitHubTransport.Request(method, path,
                Map.of("Authorization", "Bearer " + token), body);
        GitHubTransport.Response response = transport.execute(request);
        if (response.statusCode() == 401) {
            authenticator.invalidate(owner, repository);
            token = authenticator.installationToken(owner, repository);
            response = transport.execute(new GitHubTransport.Request(method, path,
                    Map.of("Authorization", "Bearer " + token), body));
        }
        return response;
    }

    private static GitHubPullRequest pullRequest(JsonNode json) {
        return new GitHubPullRequest(json.path("number").asInt(), json.path("node_id").asText(null),
                json.path("html_url").asText(null), json.path("head").path("sha").asText(null),
                json.path("base").path("ref").asText(null), json.path("state").asText(null));
    }

    private static JsonNode refJson(GitHubTransport.Response response) throws Exception {
        return MAPPER.readTree(response.body());
    }

    private static void requireStatus(GitHubTransport.Response response, int expected, String operation) {
        if (response.statusCode() != expected) {
            throw apiFailure(response, operation);
        }
    }

    private static GitHubApiException apiFailure(GitHubTransport.Response response, String operation) {
        String remaining = response.headers().entrySet().stream()
                .filter(entry -> "x-ratelimit-remaining".equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue).findFirst().orElse(null);
        String suffix = "0".equals(remaining) ? "; GitHub API rate limit exhausted" : "";
        return new GitHubApiException(response.statusCode(), "Failed to " + operation + suffix);
    }

    private static String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("GitHub API response omitted " + field);
        }
        return value;
    }

    private static String repoPath(GitHubRepositoryRef repository) {
        return repoPath(repository.owner(), repository.repository());
    }

    private static String repoPath(String owner, String repository) {
        return "/repos/" + encode(owner) + "/" + encode(repository);
    }

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String encodePath(String path) {
        return java.util.Arrays.stream(path.split("/", -1)).map(GitHubApiClient::encode)
                .collect(java.util.stream.Collectors.joining("/"));
    }

    private static String[] parseRepository(String repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            throw new IllegalArgumentException("Repository URL is required");
        }
        String value = repositoryUrl.trim();
        String path;
        if (value.startsWith("git@github.com:")) {
            path = value.substring("git@github.com:".length());
        } else {
            URI uri = URI.create(value);
            if (!"github.com".equalsIgnoreCase(uri.getHost())) {
                throw new IllegalArgumentException("Only github.com repositories are supported");
            }
            path = uri.getPath();
            if (path.startsWith("/")) path = path.substring(1);
        }
        if (path.endsWith(".git")) path = path.substring(0, path.length() - 4);
        String[] parts = path.split("/");
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("Repository URL must identify owner/repository");
        }
        return parts;
    }

    public record RepositoryContent(String sha, String content) {}
    public record ContentUpdate(String contentSha, String commitSha) {}
}
