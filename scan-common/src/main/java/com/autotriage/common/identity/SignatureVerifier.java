package com.autotriage.common.identity;

public interface SignatureVerifier {
    boolean verify(byte[] payload, SignatureEnvelope envelope);
}
