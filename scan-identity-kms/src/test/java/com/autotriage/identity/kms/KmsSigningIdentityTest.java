package com.autotriage.identity.kms;

import com.autotriage.common.identity.SignatureEnvelope;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.MessageType;
import software.amazon.awssdk.services.kms.model.SignRequest;
import software.amazon.awssdk.services.kms.model.SignResponse;
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec;
import software.amazon.awssdk.services.kms.model.VerifyRequest;
import software.amazon.awssdk.services.kms.model.VerifyResponse;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KmsSigningIdentityTest {
    private static final String KEY_ARN = "arn:aws:kms:us-east-1:123456789012:key/test";
    private static final PSSParameterSpec PSS_SHA_256 =
            new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1);

    @Test
    void signsDigestWithPssAndProducesVerifiableEnvelope() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        KmsClient kms = mock(KmsClient.class);
        when(kms.sign(any(SignRequest.class))).thenAnswer(invocation -> {
            SignRequest request = invocation.getArgument(0);
            assertEquals(KEY_ARN, request.keyId());
            assertEquals(MessageType.DIGEST, request.messageType());
            assertEquals(SigningAlgorithmSpec.RSASSA_PSS_SHA_256, request.signingAlgorithm());
            Signature signer = Signature.getInstance("RSASSA-PSS");
            signer.setParameter(PSS_SHA_256);
            signer.initSign(pair.getPrivate());
            signer.update(request.message().asByteArray());
            return SignResponse.builder().signature(SdkBytes.fromByteArray(signer.sign())).keyId(KEY_ARN).build();
        });
        when(kms.verify(any(VerifyRequest.class))).thenAnswer(invocation -> {
            VerifyRequest request = invocation.getArgument(0);
            Signature verifier = Signature.getInstance("RSASSA-PSS");
            verifier.setParameter(PSS_SHA_256);
            verifier.initVerify(pair.getPublic());
            verifier.update(request.message().asByteArray());
            return VerifyResponse.builder().signatureValid(verifier.verify(request.signature().asByteArray())).build();
        });
        byte[] payload = "- fingerprint: deterministic\n".getBytes(StandardCharsets.UTF_8);
        Clock clock = Clock.fixed(Instant.parse("2026-07-28T12:00:00Z"), ZoneOffset.UTC);

        SignatureEnvelope envelope = new KmsSigningIdentity(kms, KEY_ARN, clock, Map.of("service", "triage"))
                .sign(payload);

        assertEquals("RSASSA_PSS_SHA_256", envelope.algorithm());
        assertEquals(clock.instant(), envelope.signedAt());
        assertTrue(new KmsSignatureVerifier(kms, Set.of(KEY_ARN)).verify(payload, envelope));
        assertFalse(new KmsSignatureVerifier(kms, Set.of(KEY_ARN))
                .verify("tampered".getBytes(StandardCharsets.UTF_8), envelope));
        assertFalse(new KmsSignatureVerifier(kms,
                Set.of("arn:aws:kms:us-east-1:123456789012:key/other"), clock)
                .verify(payload, envelope));
    }

    @Test
    void requiresAnAllowedKey() {
        assertThrows(IllegalArgumentException.class,
                () -> new KmsSignatureVerifier(mock(KmsClient.class), Set.of()));
    }
}
