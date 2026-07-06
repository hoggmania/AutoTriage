package com.autotriage.worker.light.security;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

public class ConfigurableSuppressionSignatureVerifier implements SuppressionSignatureVerifier {

    private static final Logger log = Logger.getLogger(ConfigurableSuppressionSignatureVerifier.class);
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String TEST_SIGNATURE = "TEST-SIGNATURE";

    @Override
    public boolean verify(Path bundlePath) {
        Optional<String> signingSecret = signingSecret();
        if (signingSecret.isEmpty() && !allowTestSignature()) {
            log.warn("Suppression signing secret is not configured and test signatures are disabled");
            return false;
        }
        try {
            Map<String, byte[]> entries = readBundleEntries(bundlePath);
            boolean sawSuppression = false;
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                String name = entry.getKey();
                if (!isSuppressionYaml(name)) {
                    continue;
                }
                sawSuppression = true;
                byte[] signatureBytes = entries.get(name + ".sig");
                if (signatureBytes == null) {
                    log.warnv("Missing signature for suppression file {0}", name);
                    return false;
                }
                String signature = new String(signatureBytes, StandardCharsets.UTF_8).trim();
                if (!verifySignature(entry.getValue(), signature, signingSecret)) {
                    log.warnv("Invalid signature for suppression file {0}", name);
                    return false;
                }
            }
            if (!sawSuppression) {
                log.warn("Suppression bundle contains no YAML suppression files");
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warnv("Failed to verify suppression bundle {0}: {1}", bundlePath, e.getMessage());
            return false;
        }
    }

    private boolean verifySignature(byte[] content, String signature, Optional<String> signingSecret) throws Exception {
        if (signingSecret.isPresent()) {
            String expected = sign(content, signingSecret.get());
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        }
        return allowTestSignature() && TEST_SIGNATURE.equals(signature);
    }

    public static String sign(byte[] content, String secret) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
        return "hmac-sha256:" + HexFormat.of().formatHex(mac.doFinal(content));
    }

    public static Optional<String> signingSecret() {
        return ConfigProvider.getConfig()
                .getOptionalValue("suppression.signature.hmac-secret", String.class)
                .filter(secret -> !secret.isBlank());
    }

    public static boolean allowTestSignature() {
        return ConfigProvider.getConfig()
                .getOptionalValue("suppression.signature.allow-test-signature", Boolean.class)
                .orElse(false);
    }

    private static boolean isSuppressionYaml(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".yaml") || lower.endsWith(".yml");
    }

    private static Map<String, byte[]> readBundleEntries(Path bundlePath) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (InputStream fileIn = Files.newInputStream(bundlePath);
             BufferedInputStream buffered = new BufferedInputStream(fileIn);
             GzipCompressorInputStream gzipIn = new GzipCompressorInputStream(buffered);
             TarArchiveInputStream tarIn = new TarArchiveInputStream(gzipIn)) {
            TarArchiveEntry entry;
            while ((entry = tarIn.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                tarIn.transferTo(out);
                entries.put(entry.getName(), out.toByteArray());
            }
        }
        return entries;
    }
}
