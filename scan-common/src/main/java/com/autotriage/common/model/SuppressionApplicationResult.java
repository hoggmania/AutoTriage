package com.autotriage.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public final class SuppressionApplicationResult {
    private final ArtifactRef finalSarif;
    private final ArtifactRef suppressionReport;

    @JsonCreator
    public SuppressionApplicationResult(
            @JsonProperty("finalSarif") ArtifactRef finalSarif,
            @JsonProperty("suppressionReport") ArtifactRef suppressionReport) {
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
