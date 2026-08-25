package com.autotriage.triage.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class GitHubAppAuthenticator {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long REFRESH_SKEW_SECONDS = 60;

    private final long appId;
    private final PrivateKey privateKey;
    private final GitHubTransport transport;
    private final Clock clock;
    private final int maxCachedTokens;
    private final Map<String, CachedToken> tokenCache = new LinkedHashMap<>(16, 0.75f, true);

    @Inject
    public GitHubAppAuthenticator(GitHubTransport transport) {
        this(readAppId(), readConfiguredPrivateKey(), transport, Clock.systemUTC(),
                ConfigProvider.getConfig().getOptionalValue("github.app.token-cache-max-entries", Integer.class)
                        .orElse(100));
    }

    public GitHubAppAuthenticator(long appId, PrivateKey privateKey, GitHubTransport transport,
                                  Clock clock, int maxCachedTokens) {
        if (appId <= 0 || privateKey == null || transport == null || clock == null || maxCachedTokens <= 0) {
            throw new IllegalArgumentException("GitHub App authentication configuration is invalid");
        }
        this.appId = appId;
        this.privateKey = privateKey;
        this.transport = transport;
        this.clock = clock;
        this.maxCachedTokens = maxCachedTokens;
    }

    public synchronized String installationToken(String owner, String repository) throws Exception {
        String cacheKey = owner + "/" + repository;
        Instant now = clock.instant();
        CachedToken cached = tokenCache.get(cacheKey);
        if (cached != null && now.isBefore(cached.expiresAt.minusSeconds(REFRESH_SKEW_SECONDS))) {
            return cached.value;
        }

        long installationId = resolveInstallationId(owner, repository);
        GitHubTransport.Response response = transport.execute(new GitHubTransport.Request(
                "POST", "/app/installations/" + installationId + "/access_tokens",
                appHeaders(), "{}"));
        requireStatus(response, 201, "exchange GitHub App installation token");
        JsonNode json = MAPPER.readTree(response.body());
        String token = requiredText(json, "token");
        Instant expiresAt = Instant.parse(requiredText(json, "expires_at"));
        tokenCache.put(cacheKey, new CachedToken(token, expiresAt));
        while (tokenCache.size() > maxCachedTokens) {
            String eldest = tokenCache.keySet().iterator().next();
            tokenCache.remove(eldest);
        }
        return token;
    }

    public synchronized void invalidate(String owner, String repository) {
        tokenCache.remove(owner + "/" + repository);
    }

    String appJwt() throws Exception {
        Instant now = clock.instant();
        String header = base64Url(MAPPER.writeValueAsBytes(Map.of("alg", "RS256", "typ", "JWT")));
        String payload = base64Url(MAPPER.writeValueAsBytes(Map.of(
                "iat", now.minusSeconds(60).getEpochSecond(),
                "exp", now.plusSeconds(540).getEpochSecond(),
                "iss", Long.toString(appId))));
        String signingInput = header + "." + payload;
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + base64Url(signature.sign());
    }

    public static PrivateKey parsePrivateKey(String pem) {
        try {
            if (pem == null || pem.isBlank()) {
                throw new IllegalArgumentException("GitHub App private key is not configured");
            }
            String normalized = pem.replace("\\n", "\n").trim();
            boolean pkcs1 = normalized.contains("BEGIN RSA PRIVATE KEY");
            String keyLabel = "PRIVATE" + " KEY";
            String rsaKeyLabel = "RSA " + keyLabel;
            String base64 = normalized
                    .replace("-----BEGIN " + keyLabel + "-----", "")
                    .replace("-----END " + keyLabel + "-----", "")
                    .replace("-----BEGIN " + rsaKeyLabel + "-----", "")
                    .replace("-----END " + rsaKeyLabel + "-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(base64);
            if (pkcs1) {
                keyBytes = wrapPkcs1InPkcs8(keyBytes);
            }
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("GitHub App private key is invalid", e);
        }
    }

    private long resolveInstallationId(String owner, String repository) throws Exception {
        GitHubTransport.Response response = transport.execute(new GitHubTransport.Request(
                "GET", "/repos/" + GitHubApiClient.encode(owner) + "/" + GitHubApiClient.encode(repository)
                        + "/installation", appHeaders(), ""));
        requireStatus(response, 200, "resolve GitHub App installation");
        return MAPPER.readTree(response.body()).path("id").asLong(-1);
    }

    private Map<String, String> appHeaders() throws Exception {
        return Map.of("Authorization", "Bearer " + appJwt());
    }

    private static void requireStatus(GitHubTransport.Response response, int expected, String operation) {
        if (response.statusCode() != expected) {
            throw new GitHubApiException(response.statusCode(), "Failed to " + operation);
        }
    }

    private static String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("GitHub API response omitted " + field);
        }
        return value;
    }

    private static long readAppId() {
        return ConfigProvider.getConfig().getOptionalValue("github.app.id", Long.class)
                .orElseThrow(() -> new IllegalStateException("github.app.id is required"));
    }

    private static PrivateKey readConfiguredPrivateKey() {
        String inline = ConfigProvider.getConfig().getOptionalValue("github.app.private-key", String.class)
                .orElse("");
        if (!inline.isBlank()) {
            return parsePrivateKey(inline);
        }
        String path = ConfigProvider.getConfig().getOptionalValue("github.app.private-key-path", String.class)
                .orElse("");
        if (path.isBlank()) {
            throw new IllegalStateException("github.app.private-key or github.app.private-key-path is required");
        }
        try {
            return parsePrivateKey(Files.readString(Path.of(path), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read GitHub App private key", e);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] wrapPkcs1InPkcs8(byte[] pkcs1) {
        byte[] rsaAlgorithm = new byte[]{0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48,
                (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00};
        byte[] version = new byte[]{0x02, 0x01, 0x00};
        byte[] octetString = der((byte) 0x04, pkcs1);
        byte[] body = concat(version, rsaAlgorithm, octetString);
        return der((byte) 0x30, body);
    }

    private static byte[] der(byte tag, byte[] value) {
        byte[] length;
        if (value.length < 128) {
            length = new byte[]{(byte) value.length};
        } else if (value.length < 256) {
            length = new byte[]{(byte) 0x81, (byte) value.length};
        } else {
            length = new byte[]{(byte) 0x82, (byte) (value.length >> 8), (byte) value.length};
        }
        return concat(new byte[]{tag}, length, value);
    }

    private static byte[] concat(byte[]... arrays) {
        int length = 0;
        for (byte[] array : arrays) {
            length += array.length;
        }
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }

    private record CachedToken(String value, Instant expiresAt) {
    }
}
