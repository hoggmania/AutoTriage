package com.autotriage.worker.filter.model;

public class SuppressionReport {
    private final int suppressedCount;
    private final int expiredCount;
    private final int invalidCount;

    public SuppressionReport(int suppressedCount, int expiredCount, int invalidCount) {
        this.suppressedCount = suppressedCount;
        this.expiredCount = expiredCount;
        this.invalidCount = invalidCount;
    }

    public int getSuppressedCount() {
        return suppressedCount;
    }

    public int getExpiredCount() {
        return expiredCount;
    }

    public int getInvalidCount() {
        return invalidCount;
    }
}
