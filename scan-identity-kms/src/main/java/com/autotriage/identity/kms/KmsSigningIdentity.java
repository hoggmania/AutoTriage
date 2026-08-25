package com.autotriage.identity.kms;

import com.autotriage.common.identity.SignatureEnvelope;
import com.autotriage.common.identity.SigningIdentity;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.MessageType;
import software.amazon.awssdk.services.kms.model.SignRequest;
import software.amazon.awssdk.services.kms.model.SignResponse;
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec;

import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

public final class KmsSigningIdentity implements SigningIdentity {
    private final KmsClient kms;
    private final String keyArn;
    private final Clock clock;
    private final Map<String, String> identityMetadata;

    public KmsSigningIdentity(String keyArn) {
        this(defaultClient(keyArn), keyArn, Clock.systemUTC(), Map.of());
    }

    public static KmsSigningIdentity fromDefaultCredentials(String keyArn, Map<String, String> identityMetadata) {
        return new KmsSigningIdentity(defaultClient(keyArn), keyArn, Clock.systemUTC(), identityMetadata);
    }

    public KmsSigningIdentity(KmsClient kms, String keyArn, Clock clock, Map<String, String> identityMetadata) {
        this.kms = Objects.requireNonNull(kms, "kms");
        this.keyArn = requireKeyArn(keyArn);
        this.clock = Objects.requireNonNull(clock, "clock");
        this.identityMetadata = identityMetadata == null ? Map.of() : Map.copyOf(identityMetadata);
    }

    @Override
    public SignatureEnvelope sign(byte[] payload) {
        byte[] digest = sha256(payload);
        SignResponse response = kms.sign(SignRequest.builder()
                .keyId(keyArn)
                .message(SdkBytes.fromByteArray(digest))
                .messageType(MessageType.DIGEST)
                .signingAlgorithm(SigningAlgorithmSpec.RSASSA_PSS_SHA_256)
                .build());
        if (response == null || response.signature() == null || response.signature().asByteArray().length == 0) {
            throw new IllegalStateException("KMS returned no signature");
        }
        String resolvedKey = response.keyId() == null ? keyArn : response.keyId();
        if (!keyArn.equals(resolvedKey)) throw new IllegalStateException("KMS signed with an unexpected key");
        return new SignatureEnvelope(SignatureEnvelope.CURRENT_VERSION, resolvedKey,
                SignatureEnvelope.KMS_PSS_SHA_256, HexFormat.of().formatHex(digest),
                Base64.getEncoder().encodeToString(response.signature().asByteArray()),
                clock.instant(), identityMetadata);
    }

    static byte[] sha256(byte[] payload) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(Objects.requireNonNull(payload, "payload"));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static KmsClient defaultClient(String keyArn) {
        String validatedArn = requireKeyArn(keyArn);
        return KmsClient.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .region(Region.of(validatedArn.split(":", 6)[3]))
                .build();
    }

    private static String requireKeyArn(String keyArn) {
        Objects.requireNonNull(keyArn, "keyArn");
        if (!keyArn.matches("arn:(aws|aws-cn|aws-us-gov):kms:[^:]+:[0-9]{12}:key/.+")) {
            throw new IllegalArgumentException("keyArn must be an AWS KMS key ARN");
        }
        return keyArn;
    }
}
