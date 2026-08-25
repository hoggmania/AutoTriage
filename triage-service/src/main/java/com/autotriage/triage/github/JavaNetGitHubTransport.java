package com.autotriage.triage.github;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.ConfigProvider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class JavaNetGitHubTransport implements GitHubTransport {
    private final HttpClient client;
    private final URI apiBase;

    public JavaNetGitHubTransport() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                URI.create(ConfigProvider.getConfig()
                        .getOptionalValue("github.api.base-url", String.class)
                        .orElse("https://api.github.com")));
    }

    JavaNetGitHubTransport(HttpClient client, URI apiBase) {
        this.client = client;
        this.apiBase = apiBase;
    }

    @Override
    public Response execute(Request request) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(apiBase.resolve(request.path()))
                .timeout(Duration.ofSeconds(30));
        request.headers().forEach(builder::header);
        builder.header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "AutoTriage");
        if (request.body().isEmpty()) {
            builder.method(request.method(), HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(request.method(), HttpRequest.BodyPublishers.ofString(request.body()));
        }
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        Map<String, String> headers = new LinkedHashMap<>();
        response.headers().map().forEach((name, values) -> {
            if (!values.isEmpty()) {
                headers.put(name, values.get(0));
            }
        });
        return new Response(response.statusCode(), headers, response.body());
    }
}
