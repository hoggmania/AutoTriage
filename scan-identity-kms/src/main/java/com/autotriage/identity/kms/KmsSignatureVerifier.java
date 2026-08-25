package com.autotriage.identity.kms;

import com.autotriage.common.identity.SignatureEnvelope;
import com.autotriage.common.identity.SignatureVerifier;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.MessageType;
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec;
import software.amazon.awssdk.services.kms.model.VerifyRequest;

import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;

public final class KmsSignatureVerifier implements SignatureVerifier {
    private static final Duration MAX_ENVELOPE_AGE = Duration.ofDays(3650);
    private final KmsClient kms;
    private final Set<String> allowedKeyArns;
    private final Clock clock;

    public KmsSignatureVerifier(Set<String> allowedKeyArns) {
        this(defaultClient(allowedKeyArns), allowedKeyArns, Clock.systemUTC());
    }

    public static KmsSignatureVerifier fromDefaultCredentials(Set<String> allowedKeyArns) {
        return new KmsSignatureVerifier(allowedKeyArns);
    }

    public KmsSignatureVerifier(KmsClient kms, Set<String> allowedKeyArns) {
        this(kms, allowedKeyArns, Clock.systemUTC());
    }

    public KmsSignatureVerifier(KmsClient kms, Set<String> allowedKeyArns, Clock clock) {
        this.kms = Objects.requireNonNull(kms, "kms");
        this.allowedKeyArns = Set.copyOf(Objects.requireNonNull(allowedKeyArns, "allowedKeyArns"));
        if (this.allowedKeyArns.isEmpty()) {
            throw new IllegalArgumentException("At least one allowed KMS key ARN is required");
        }
        if (this.allowedKeyArns.stream().anyMatch(arn -> !isKeyArn(arn))) {
            throw new IllegalArgumentException("Allowed keys must be AWS KMS key ARNs");
        }
        if (this.allowedKeyArns.stream().map(KmsSignatureVerifier::region).distinct().count() != 1) {
            throw new IllegalArgumentException("Allowed KMS key ARNs must belong to one AWS region");
        }
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public boolean verify(byte[] payload, SignatureEnvelope envelope) {
        try {
            if (envelope == null || !allowedKeyArns.contains(envelope.keyArn())) return false;
            if (envelope.version() != SignatureEnvelope.CURRENT_VERSION
                    || !SignatureEnvelope.KMS_PSS_SHA_256.equals(envelope.algorithm())) return false;
            if (envelope.signedAt().isAfter(clock.instant().plusSeconds(60))
                    || envelope.signedAt().isBefore(clock.instant().minus(MAX_ENVELOPE_AGE))) return false;
            byte[] digest = KmsSigningIdentity.sha256(payload);
            if (!HexFormat.of().formatHex(digest).equals(envelope.payloadSha256())) return false;
            return kms.verify(VerifyRequest.builder()
                    .keyId(envelope.keyArn())
                    .message(SdkBytes.fromByteArray(digest))
                    .messageType(MessageType.DIGEST)
                    .signingAlgorithm(SigningAlgorithmSpec.RSASSA_PSS_SHA_256)
                    .signature(SdkBytes.fromByteArray(Base64.getDecoder().decode(envelope.signature())))
                    .build()).signatureValid();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static KmsClient defaultClient(Set<String> allowedKeyArns) {
        Objects.requireNonNull(allowedKeyArns, "allowedKeyArns");
        if (allowedKeyArns.isEmpty()) {
            throw new IllegalArgumentException("At least one allowed KMS key ARN is required");
        }
        String firstArn = allowedKeyArns.iterator().next();
        if (!isKeyArn(firstArn)) {
            throw new IllegalArgumentException("Allowed keys must be AWS KMS key ARNs");
        }
        return KmsClient.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .region(Region.of(region(firstArn)))
                .build();
    }

    private static boolean isKeyArn(String arn) {
        return arn != null && arn.matches("arn:(aws|aws-cn|aws-us-gov):kms:[^:]+:[0-9]{12}:key/.+");
    }

    private static String region(String arn) {
        return arn.split(":", 6)[3];
    }
}
