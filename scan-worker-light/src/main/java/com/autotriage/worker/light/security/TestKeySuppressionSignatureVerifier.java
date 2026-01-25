package com.autotriage.worker.light.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestKeySuppressionSignatureVerifier implements SuppressionSignatureVerifier {

    private static final String TEST_PUBLIC_KEY = "TEST_PUBLIC_KEY";
    private static final String EXPECTED_SIGNATURE = "TEST-SIGNATURE";

    @Override
    public boolean verify(Path bundlePath) {
        Path signaturePath = Path.of(bundlePath.toString() + ".sig");
        if (!Files.exists(signaturePath)) {
            return false;
        }
        try {
            String signature = Files.readString(signaturePath, StandardCharsets.UTF_8).trim();
            return EXPECTED_SIGNATURE.equals(signature) && !TEST_PUBLIC_KEY.isEmpty();
        } catch (IOException e) {
            return false;
        }
    }
}
