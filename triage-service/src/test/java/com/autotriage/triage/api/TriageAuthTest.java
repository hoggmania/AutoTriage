package com.autotriage.triage.api;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Set;

import static io.restassured.RestAssured.given;

@QuarkusTest
class TriageAuthTest {

    private static final String ISSUER = "https://autotriage.test";
    private static final String ROLE = "security:vuln_assessor:triager";

    @Test
    void deniesAccessWithoutToken() {
        given()
                .when()
                .get("/triage/findings")
                .then()
                .statusCode(401);
    }

    @Test
    void allowsAccessWithRoleToken() {
        String token = buildToken(ROLE);
        given()
                .auth()
                .oauth2(token)
                .when()
                .get("/triage/findings")
                .then()
                .statusCode(200);
    }

    private String buildToken(String role) {
        return Jwt.issuer(ISSUER)
                .upn("tester")
                .groups(Set.of(role))
                .sign(readPrivateKey());
    }

    private PrivateKey readPrivateKey() {
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
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load JWT private key", e);
        }
    }
}
