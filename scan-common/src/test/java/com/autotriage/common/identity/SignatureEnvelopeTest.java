package com.autotriage.common.identity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SignatureEnvelopeTest {
    @Test
    void requiresVersionedKmsPssEnvelopeFields() {
        SignatureEnvelope envelope = new SignatureEnvelope(
                1,
                "arn:aws:kms:us-east-1:123456789012:key/key-id",
                "RSASSA_PSS_SHA_256",
                "a".repeat(64),
                "c2lnbmF0dXJl",
                Instant.parse("2026-07-28T12:00:00Z"),
                Map.of("service", "triage-service"));

        assertEquals(1, envelope.version());
        assertEquals("triage-service", envelope.identityMetadata().get("service"));
        assertEquals(envelope, SignatureEnvelopeJson.read(SignatureEnvelopeJson.write(envelope)));
    }

    @Test
    void rejectsUnsupportedAlgorithm() {
        assertThrows(IllegalArgumentException.class, () -> new SignatureEnvelope(
                1, "arn:aws:kms:us-east-1:123456789012:key/key-id", "HMAC_SHA_256",
                "a".repeat(64), "c2ln", Instant.EPOCH, Map.of()));
    }
}
