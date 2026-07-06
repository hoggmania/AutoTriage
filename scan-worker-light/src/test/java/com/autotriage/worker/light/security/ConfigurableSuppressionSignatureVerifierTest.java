package com.autotriage.worker.light.security;

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
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurableSuppressionSignatureVerifierTest {

    private Path tempRoot;

    @AfterEach
    void cleanup() throws Exception {
        System.clearProperty("suppression.signature.hmac-secret");
        System.clearProperty("suppression.signature.allow-test-signature");
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
    void verifiesHmacSignedSuppressionYamlInsideBundle() throws Exception {
        tempRoot = Files.createTempDirectory("suppression-signature-test-");
        System.setProperty("suppression.signature.hmac-secret", "test-secret");
        byte[] suppression = "- fingerprint: fp-1\n".getBytes(StandardCharsets.UTF_8);
        String signature = ConfigurableSuppressionSignatureVerifier.sign(suppression, "test-secret");
        Path bundle = tempRoot.resolve("suppressions.tar.gz");
        writeBundle(bundle, suppression, signature);

        assertTrue(new ConfigurableSuppressionSignatureVerifier().verify(bundle));
    }

    @Test
    void rejectsUnsignedBundleWhenTestSignatureGuardDisabled() throws Exception {
        tempRoot = Files.createTempDirectory("suppression-signature-test-");
        byte[] suppression = "- fingerprint: fp-1\n".getBytes(StandardCharsets.UTF_8);
        Path bundle = tempRoot.resolve("suppressions.tar.gz");
        writeBundle(bundle, suppression, "TEST-SIGNATURE");

        assertFalse(new ConfigurableSuppressionSignatureVerifier().verify(bundle));
    }

    @Test
    void acceptsTestSignatureOnlyWhenGuardEnabled() throws Exception {
        tempRoot = Files.createTempDirectory("suppression-signature-test-");
        System.setProperty("suppression.signature.allow-test-signature", "true");
        byte[] suppression = "- fingerprint: fp-1\n".getBytes(StandardCharsets.UTF_8);
        Path bundle = tempRoot.resolve("suppressions.tar.gz");
        writeBundle(bundle, suppression, "TEST-SIGNATURE");

        assertTrue(new ConfigurableSuppressionSignatureVerifier().verify(bundle));
    }

    private static void writeBundle(Path bundle, byte[] suppression, String signature) throws Exception {
        try (OutputStream fileOut = Files.newOutputStream(bundle);
             BufferedOutputStream buffered = new BufferedOutputStream(fileOut);
             GzipCompressorOutputStream gzipOut = new GzipCompressorOutputStream(buffered);
             TarArchiveOutputStream tarOut = new TarArchiveOutputStream(gzipOut)) {
            writeEntry(tarOut, "suppressions.yaml", suppression);
            writeEntry(tarOut, "suppressions.yaml.sig", signature.getBytes(StandardCharsets.UTF_8));
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
