package com.autotriage.worker.filter.zerofalse;

public record ZeroFalseVerdict(boolean falsePositive,
                               String sanitizationFound,
                               String attackFeasible,
                               String confidence) {
}
