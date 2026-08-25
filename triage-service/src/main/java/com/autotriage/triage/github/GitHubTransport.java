package com.autotriage.triage.github;

import java.util.Map;

/** HTTP boundary for GitHub REST calls. Tests use an in-memory fake. */
public interface GitHubTransport {
    Response execute(Request request) throws Exception;

    record Request(String method, String path, Map<String, String> headers, String body) {
        public Request {
            headers = headers == null ? Map.of() : Map.copyOf(headers);
            body = body == null ? "" : body;
        }
    }

    record Response(int statusCode, Map<String, String> headers, String body) {
        public Response {
            headers = headers == null ? Map.of() : Map.copyOf(headers);
            body = body == null ? "" : body;
        }
    }
}
