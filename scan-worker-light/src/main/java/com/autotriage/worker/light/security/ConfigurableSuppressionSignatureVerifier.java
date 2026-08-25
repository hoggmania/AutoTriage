package com.autotriage.worker.light.security;

import com.autotriage.common.identity.SignatureEnvelope;
import com.autotriage.common.identity.SignatureEnvelopeJson;
import com.autotriage.common.identity.SignatureVerifier;
import com.autotriage.identity.kms.KmsSignatureVerifier;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ConfigurableSuppressionSignatureVerifier implements SuppressionSignatureVerifier {

    public static final String ALLOWED_KEY_ARNS_CONFIG = "suppression.signature.allowed-kms-key-arns";
    private static final Logger log = Logger.getLogger(ConfigurableSuppressionSignatureVerifier.class);

    private volatile SignatureVerifier verifier;

    public ConfigurableSuppressionSignatureVerifier() {
    }

    public ConfigurableSuppressionSignatureVerifier(SignatureVerifier verifier) {
        this.verifier = Objects.requireNonNull(verifier, "verifier");
    }

    @Override
    public boolean verify(Path bundlePath) {
        try {
            SignatureVerifier configuredVerifier = verifier();
            if (configuredVerifier == null) {
                log.warnv("No allowed KMS key ARNs configured in {0}", ALLOWED_KEY_ARNS_CONFIG);
                return false;
            }
            Map<String, byte[]> entries = readBundleEntries(bundlePath);
            boolean sawSuppression = false;
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                String name = entry.getKey();
                if (!isSuppressionYaml(name)) {
                    continue;
                }
                sawSuppression = true;
                byte[] envelopeJson = entries.get(name + ".sig");
                if (envelopeJson == null) {
                    log.warnv("Missing signature envelope for suppression file {0}", name);
                    return false;
                }
                SignatureEnvelope envelope = SignatureEnvelopeJson.read(envelopeJson);
                if (!configuredVerifier.verify(entry.getValue(), envelope)) {
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

    private SignatureVerifier verifier() {
        SignatureVerifier current = verifier;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (verifier == null) {
                Set<String> allowedKeyArns = allowedKeyArns();
                if (allowedKeyArns.isEmpty()) {
                    return null;
                }
                verifier = KmsSignatureVerifier.fromDefaultCredentials(allowedKeyArns);
            }
            return verifier;
        }
    }

    static Set<String> allowedKeyArns() {
        return ConfigProvider.getConfig()
                .getOptionalValue(ALLOWED_KEY_ARNS_CONFIG, String.class)
                .stream()
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
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
