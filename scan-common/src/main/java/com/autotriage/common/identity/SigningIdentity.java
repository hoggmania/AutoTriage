package com.autotriage.common.identity;

public interface SigningIdentity {
    SignatureEnvelope sign(byte[] payload);
}
