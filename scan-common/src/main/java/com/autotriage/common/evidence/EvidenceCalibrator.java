package com.autotriage.common.evidence;

@FunctionalInterface
public interface EvidenceCalibrator {
    EvidenceAssessment calibrate(Evidence evidence);
}
