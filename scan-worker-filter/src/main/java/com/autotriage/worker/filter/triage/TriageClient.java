package com.autotriage.worker.filter.triage;

import com.autotriage.common.model.TriageCandidateRequest;
import com.autotriage.common.model.TriageCandidateResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

@jakarta.enterprise.context.ApplicationScoped
public class TriageClient {

    private static final Logger log = Logger.getLogger(TriageClient.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient client = HttpClient.newHttpClient();

    public Optional<TriageCandidateResponse> submitCandidate(TriageCandidateRequest request) {
        String baseUrl = ConfigProvider.getConfig()
                .getOptionalValue("triage.service.url", String.class)
                .orElse("");
        if (baseUrl.isBlank()) {
            return Optional.empty();
        }
        String endpoint = baseUrl.endsWith("/") ? baseUrl + "triage/candidates" : baseUrl + "/triage/candidates";
        try {
            String body = mapper.writeValueAsString(request);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warnv("Triage service returned status {0}", response.statusCode());
                return Optional.empty();
            }
            return Optional.of(mapper.readValue(response.body(), TriageCandidateResponse.class));
        } catch (Exception e) {
            log.warnv("Triage service request failed: {0}", e.getMessage());
            return Optional.empty();
        }
    }
}
