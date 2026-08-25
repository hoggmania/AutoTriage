package com.autotriage.common.identity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;

/** Strict JSON codec for detached suppression signature envelopes. */
public final class SignatureEnvelopeJson {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private SignatureEnvelopeJson() {
    }

    public static byte[] write(SignatureEnvelope envelope) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize signature envelope", e);
        }
    }

    public static SignatureEnvelope read(byte[] json) {
        try {
            return MAPPER.readValue(json, SignatureEnvelope.class);
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("Invalid signature envelope JSON", e);
        }
    }
}