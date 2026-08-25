package com.autotriage.triage.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubAppAuthenticatorTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void createsRs256AppJwtAndCachesInstallationToken() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        FakeTransport transport = new FakeTransport(
                new GitHubTransport.Response(200, Map.of(), "{\"id\":42}"),
                new GitHubTransport.Response(201, Map.of(),
                        "{\"token\":\"installation-secret\",\"expires_at\":\"2026-08-10T13:00:00Z\"}"));
        Clock clock = Clock.fixed(Instant.parse("2026-08-10T12:00:00Z"), ZoneOffset.UTC);
        GitHubAppAuthenticator authenticator = new GitHubAppAuthenticator(
                1234L, keyPair.getPrivate(), transport, clock, 10);

        String first = authenticator.installationToken("acme", "widget");
        String second = authenticator.installationToken("acme", "widget");

        assertEquals("installation-secret", first);
        assertEquals(first, second);
        assertEquals(2, transport.requests.size());
        GitHubTransport.Request installationRequest = transport.requests.get(0);
        assertEquals("/repos/acme/widget/installation", installationRequest.path());
        String jwt = installationRequest.headers().get("Authorization").substring("Bearer ".length());
        String[] parts = jwt.split("\\.");
        assertEquals(3, parts.length);
        JsonNode header = decodeJson(parts[0]);
        JsonNode payload = decodeJson(parts[1]);
        assertEquals("RS256", header.path("alg").asText());
        assertEquals("1234", payload.path("iss").asText());
        assertTrue(payload.path("exp").asLong() - payload.path("iat").asLong() <= 600);
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(keyPair.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertTrue(verifier.verify(Base64.getUrlDecoder().decode(parts[2])));
    }

    private static JsonNode decodeJson(String value) throws Exception {
        return MAPPER.readTree(Base64.getUrlDecoder().decode(value));
    }

    private static final class FakeTransport implements GitHubTransport {
        private final ArrayDeque<Response> responses;
        private final java.util.ArrayList<Request> requests = new java.util.ArrayList<>();

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
