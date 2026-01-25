package com.autotriage.worker.light.security;

import java.nio.file.Path;

public interface SuppressionSignatureVerifier {
    boolean verify(Path bundlePath);
}
