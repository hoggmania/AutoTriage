package com.autotriage.common.model;

import java.util.Objects;

public final class SuppressionApplicationResult {
    private final ArtifactRef finalSarif;
    private final ArtifactRef suppressionReport;

    public SuppressionApplicationResult(ArtifactRef finalSarif, ArtifactRef suppressionReport) {
        this.finalSarif = Objects.requireNonNull(finalSarif, "finalSarif");
        this.suppressionReport = Objects.requireNonNull(suppressionReport, "suppressionReport");
    }

    public ArtifactRef getFinalSarif() {
        return finalSarif;
    }

    public ArtifactRef getSuppressionReport() {
        return suppressionReport;
    }
}
