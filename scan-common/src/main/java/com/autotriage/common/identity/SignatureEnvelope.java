package com.autotriage.common.identity;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;

public record SignatureEnvelope(
        int version,
        String keyArn,
        String algorithm,
        String payloadSha256,
        String signature,
        Instant signedAt,
        Map<String, String> identityMetadata) {

    public static final int CURRENT_VERSION = 1;
    public static final String KMS_PSS_SHA_256 = "RSASSA_PSS_SHA_256";

    public SignatureEnvelope {
        if (version != CURRENT_VERSION) throw new IllegalArgumentException("Unsupported signature envelope version");
        if (keyArn == null || !keyArn.matches("arn:(aws|aws-cn|aws-us-gov):kms:[^:]+:[0-9]{12}:key/.+")) {
            throw new IllegalArgumentException("keyArn must be an AWS KMS key ARN");
        }
        if (!KMS_PSS_SHA_256.equals(algorithm)) throw new IllegalArgumentException("Unsupported signature algorithm");
        if (payloadSha256 == null || !payloadSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("payloadSha256 must be a lowercase SHA-256 digest");
        }
        if (signature == null || signature.isBlank()) throw new IllegalArgumentException("signature is required");
        try {
            if (Base64.getDecoder().decode(signature).length == 0) {
                throw new IllegalArgumentException("signature must contain base64-encoded bytes");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("signature must contain base64-encoded bytes", e);
        }
        if (signedAt == null) throw new IllegalArgumentException("signedAt is required");
        identityMetadata = identityMetadata == null ? Map.of() : Map.copyOf(identityMetadata);
    }
}
