package com.autotriage.common.evidence;

/** Raw scanner severity. This is deliberately separate from calibrated evidence strength. */
public enum FindingSeverity {
    INFORMATIONAL, LOW, MEDIUM, HIGH, CRITICAL
}