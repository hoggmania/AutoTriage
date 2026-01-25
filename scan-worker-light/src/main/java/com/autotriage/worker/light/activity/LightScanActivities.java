package com.autotriage.worker.light.activity;

import com.autotriage.common.activity.ScanActivities;
import com.autotriage.common.model.ArtifactRef;
import com.autotriage.common.model.ScanRequest;
import com.autotriage.common.model.ScanState;
import com.autotriage.common.model.ScanStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class LightScanActivities implements ScanActivities {

    private static final Logger log = Logger.getLogger(LightScanActivities.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public ArtifactRef resolveRepoSource(ScanRequest request) {
        log.infov("resolveRepoSource runId={0} repo={1} sha={2}", request.getRunId(), request.getRepository(), request.getCommitSha());
        return new ArtifactRef("stub://source/" + request.getRunId(), "source-archive");
    }

    @Override
    public ArtifactRef fetchSuppressionBundle(String repository, String ref) {
        log.infov("fetchSuppressionBundle repo={0} ref={1}", repository, ref);
        return new ArtifactRef("stub://suppressions/" + ref, "suppression-bundle");
    }

    @Override
    public boolean verifySuppressionSignature(ArtifactRef bundle) {
        log.infov("verifySuppressionSignature uri={0}", bundle.getUri());
        return true;
    }

    @Override
    public ArtifactRef runOpenGrep(ArtifactRef source, String runId) {
        throw new UnsupportedOperationException("runOpenGrep is handled by opengrep worker");
    }

    @Override
    public ArtifactRef applySuppressions(ArtifactRef rawSarif, ArtifactRef suppressionBundle) {
        throw new UnsupportedOperationException("applySuppressions is handled by filter worker");
    }

    @Override
    public void uploadResults(String runId, ArtifactRef finalSarif, ArtifactRef rawSarif) {
        String baseUrl = ConfigProvider.getConfig()
                .getOptionalValue("suppression.service.url", String.class)
                .orElse("http://localhost:8090");
        String endpoint = baseUrl.endsWith("/") ? baseUrl + "ingest" : baseUrl + "/ingest";
        Map<String, Object> payload = new HashMap<>();
        payload.put("runId", runId);
        payload.put("rawSarifUri", rawSarif.getUri());
        payload.put("finalSarifUri", finalSarif.getUri());
        payload.put("source", "autotriage");
        try {
            String body = mapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Suppression service returned status " + response.statusCode());
            }
            JsonNode json = mapper.readTree(response.body());
            log.infov("uploadResults runId={0} reportUrl={1} dashboardUrl={2}", runId, json.path("reportUrl").asText(), json.path("dashboardUrl").asText());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to upload results", e);
        }
    }

    @Override
    public ScanStatus computeVerdict(String runId, ArtifactRef finalSarif) {
        log.infov("computeVerdict runId={0} finalUri={1}", runId, finalSarif.getUri());
        return new ScanStatus(runId, ScanState.COMPLETED, "Stub verdict: PASS");
    }
}
