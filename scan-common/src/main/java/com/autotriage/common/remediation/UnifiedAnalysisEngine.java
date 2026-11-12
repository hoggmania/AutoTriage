package com.autotriage.common.remediation;
public interface UnifiedAnalysisEngine {
    UnifiedAnalysisResult analyze(FindingContext finding, int contextCharacterBudget);
}
