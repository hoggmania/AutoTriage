package com.autotriage.worker.filter.model;

public class SuppressionReport {
    private final int suppressedCount;
    private final int expiredCount;
    private final int invalidCount;
    private final int llmSuppressedCount;

    public SuppressionReport(int suppressedCount, int expiredCount, int invalidCount, int llmSuppressedCount) {
        this.suppressedCount = suppressedCount;
        this.expiredCount = expiredCount;
        this.invalidCount = invalidCount;
        this.llmSuppressedCount = llmSuppressedCount;
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

    public int getLlmSuppressedCount() {
        return llmSuppressedCount;
    }
}
