package com.autotriage.triage.suppressions;

import com.autotriage.common.identity.SignatureEnvelope;
import com.autotriage.common.identity.SignatureEnvelopeJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuppressionUpdateServiceSigningTest {
    private static final String KEY_ARN = "arn:aws:kms:us-east-1:123456789012:key/test";

    @Test
    void writesCanonicalPayloadAndVersionedJsonEnvelopeBesideIt() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode entries = mapper.createArrayNode();
        ObjectNode entry = mapper.createObjectNode();
        entry.put("reason", "triage");
        entry.put("fingerprint", "fp-1");
        entries.add(entry);
        final byte[][] signedPayload = new byte[1][];
        SuppressionUpdateService service = new SuppressionUpdateService(null, payload -> {
            signedPayload[0] = payload.clone();
            String digest = sha256(payload);
            return new SignatureEnvelope(SignatureEnvelope.CURRENT_VERSION, KEY_ARN,
                    SignatureEnvelope.KMS_PSS_SHA_256, digest,
                    Base64.getEncoder().encodeToString("signature".getBytes(StandardCharsets.UTF_8)),
                    Instant.parse("2026-08-10T12:00:00Z"), Map.of("service", "test"));
        });

        SuppressionUpdateService.SignedSuppressions signed = service.signSuppressions(entries);

        byte[] payload = signed.payload();
        assertArrayEquals(payload, signedPayload[0]);
        String canonical = new String(payload, StandardCharsets.UTF_8);
        assertTrue(canonical.indexOf("fingerprint") < canonical.indexOf("reason"));
        SignatureEnvelope envelope = SignatureEnvelopeJson.read(
                signed.signatureEnvelope());
        assertEquals(KEY_ARN, envelope.keyArn());
        assertEquals(sha256(payload), envelope.payloadSha256());
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}