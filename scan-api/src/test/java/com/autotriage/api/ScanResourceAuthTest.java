package com.autotriage.api;

import com.autotriage.common.model.ScanStatus;
import com.autotriage.common.workflow.OpenGrepPRScanWorkflow;
import io.quarkus.test.junit.mockito.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Regression tests for the unauthenticated scan API: every /scans operation now requires the submitter role. */
@QuarkusTest
class ScanResourceAuthTest {

    private static final String ISSUER = "https://autotriage.test";
    private static final String SCAN_BODY = "{\"runId\":\"run-1\",\"repository\":\"https://github.com/example/repo.git\","
            + "\"commitSha\":\"0123456789abcdef0123456789abcdef01234567\"}";

    @InjectMock
    WorkflowClient workflowClient;

    @Test
    void rejectsScanSubmissionWithoutToken() {
        given().contentType("application/json").body(SCAN_BODY)
                .when().post("/scans")
                .then().statusCode(401);

        verify(workflowClient, never()).newWorkflowStub(eq(OpenGrepPRScanWorkflow.class),
                any(WorkflowOptions.class));
    }

    @Test
    void rejectsStatusAndCancelWithoutToken() {
        given().when().get("/scans/run-1").then().statusCode(401);
        given().contentType("application/json").when().post("/scans/run-1/cancel").then().statusCode(401);

        verify(workflowClient, never()).newWorkflowStub(eq(OpenGrepPRScanWorkflow.class), anyString());
    }

    @Test
    void rejectsTokenWithoutSubmitterRole() {
        String token = buildToken("security:vuln_assessor:triager");

        given().auth().oauth2(token).contentType("application/json").body(SCAN_BODY)
                .when().post("/scans")
                .then().statusCode(403);
        given().auth().oauth2(token).when().get("/scans/run-1").then().statusCode(403);
        given().auth().oauth2(token).contentType("application/json")
                .when().post("/scans/run-1/cancel")
                .then().statusCode(403);
    }

    @Test
    void rejectsTokenFromUntrustedSigner() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String forged = Jwt.issuer(ISSUER).upn("attacker")
                .groups(Set.of(ScanResource.SUBMITTER_ROLE))
                .claim("role", List.of(ScanResource.SUBMITTER_ROLE))
                .sign(generator.generateKeyPair().getPrivate());

        given().auth().oauth2(forged).when().get("/scans/run-1").then().statusCode(401);
    }

    @Test
    void allowsStatusQueryWithSubmitterRole() {
        OpenGrepPRScanWorkflow workflow = mock(OpenGrepPRScanWorkflow.class);
        when(workflowClient.newWorkflowStub(OpenGrepPRScanWorkflow.class, "opengrep-scan-run-1")).thenReturn(workflow);
        when(workflow.queryStatus("run-1")).thenReturn(ScanStatus.running("run-1"));

        given().auth().oauth2(buildToken(ScanResource.SUBMITTER_ROLE))
                .when().get("/scans/run-1")
                .then().statusCode(200);
    }

    @Test
    void submitterReachesRequestValidation() {
        // An empty body fails validation before Temporal is touched, which proves the role check passed.
        given().auth().oauth2(buildToken(ScanResource.SUBMITTER_ROLE)).contentType("application/json").body("{}")
                .when().post("/scans")
                .then().statusCode(400);
    }

    @Test
    void healthStaysOpenForProbes() {
        given().when().get("/health").then().statusCode(200);
    }

    private static String buildToken(String role) {
        // smallrye.jwt.path.groups=role reads groups from the "role" claim; set both so the test
        // does not depend on which claim the deployment is configured to read.
        return Jwt.issuer(ISSUER)
                .upn("tester")
                .groups(Set.of(role))
                .claim("role", List.of(role))
                .sign(readPrivateKey());
    }

    private static PrivateKey readPrivateKey() {
        try (InputStream input = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("jwt-private-key.pem")) {
            if (input == null) {
                throw new IllegalStateException("jwt-private-key.pem not found in test resources");
            }
            String pem = new String(input.readAllBytes(), StandardCharsets.US_ASCII);
            String normalized = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(normalized);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load JWT private key", e);
        }
    }
}
