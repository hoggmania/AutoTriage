package com.autotriage.worker.light.security;

import com.autotriage.common.identity.SignatureEnvelope;
import com.autotriage.common.identity.SignatureEnvelopeJson;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurableSuppressionSignatureVerifierTest {
    private static final String KEY_ARN = "arn:aws:kms:us-east-1:123456789012:key/test";
    private Path tempRoot;

    @AfterEach
    void cleanup() throws Exception {
        System.clearProperty(ConfigurableSuppressionSignatureVerifier.ALLOWED_KEY_ARNS_CONFIG);
        if (tempRoot != null) {
            Files.walk(tempRoot)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    @Test
    void parsesJsonEnvelopeAndVerifiesCanonicalPayload() throws Exception {
        tempRoot = Files.createTempDirectory("suppression-signature-test-");
        byte[] suppression = "[ { \"fingerprint\" : \"fp-1\" } ]\n".getBytes(StandardCharsets.UTF_8);
        byte[] envelope = SignatureEnvelopeJson.write(envelopeFor(suppression));
        Path bundle = tempRoot.resolve("suppressions.tar.gz");
        writeBundle(bundle, suppression, envelope);
        AtomicBoolean called = new AtomicBoolean();

        boolean valid = new ConfigurableSuppressionSignatureVerifier((payload, parsed) -> {
            called.set(true);
            return MessageDigest.isEqual(suppression, payload) && KEY_ARN.equals(parsed.keyArn());
        }).verify(bundle);

        assertTrue(valid);
        assertTrue(called.get());
    }

    @Test
    void rejectsMalformedEnvelopeWithoutCallingVerifier() throws Exception {
        tempRoot = Files.createTempDirectory("suppression-signature-test-");
        byte[] suppression = "[]\n".getBytes(StandardCharsets.UTF_8);
        Path bundle = tempRoot.resolve("suppressions.tar.gz");
        writeBundle(bundle, suppression, "TEST-SIGNATURE".getBytes(StandardCharsets.UTF_8));
        AtomicBoolean called = new AtomicBoolean();

        assertFalse(new ConfigurableSuppressionSignatureVerifier((payload, envelope) -> {
            called.set(true);
            return true;
        }).verify(bundle));
        assertFalse(called.get());
    }

    @Test
    void failsClosedWhenKmsAllowlistIsNotConfigured() throws Exception {
        tempRoot = Files.createTempDirectory("suppression-signature-test-");
        byte[] suppression = "[]\n".getBytes(StandardCharsets.UTF_8);
        Path bundle = tempRoot.resolve("suppressions.tar.gz");
        writeBundle(bundle, suppression, SignatureEnvelopeJson.write(envelopeFor(suppression)));

        assertFalse(new ConfigurableSuppressionSignatureVerifier().verify(bundle));
    }

    private static SignatureEnvelope envelopeFor(byte[] payload) throws Exception {
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        return new SignatureEnvelope(SignatureEnvelope.CURRENT_VERSION, KEY_ARN,
                SignatureEnvelope.KMS_PSS_SHA_256, digest, "c2lnbmF0dXJl",
                Instant.parse("2026-08-10T12:00:00Z"), Map.of("service", "triage-service"));
    }

    private static void writeBundle(Path bundle, byte[] suppression, byte[] envelope) throws Exception {
        try (OutputStream fileOut = Files.newOutputStream(bundle);
             BufferedOutputStream buffered = new BufferedOutputStream(fileOut);
             GzipCompressorOutputStream gzipOut = new GzipCompressorOutputStream(buffered);
             TarArchiveOutputStream tarOut = new TarArchiveOutputStream(gzipOut)) {
            writeEntry(tarOut, "suppressions.yaml", suppression);
            writeEntry(tarOut, "suppressions.yaml.sig", envelope);
        }
    }

    private static void writeEntry(TarArchiveOutputStream tarOut, String name, byte[] bytes) throws Exception {
        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setSize(bytes.length);
        tarOut.putArchiveEntry(entry);
        tarOut.write(bytes);
        tarOut.closeArchiveEntry();
    }
}
