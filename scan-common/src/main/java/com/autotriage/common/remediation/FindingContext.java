package com.autotriage.common.remediation;

import java.util.List;
import java.util.Objects;

/** Scanner evidence and trusted, preselected source snippets for a single finding. */
public record FindingContext(String findingId, String ruleId, String message,
                             List<String> taintTrace, List<SourceSlice> sourceSlices) {
    public FindingContext {
        if (findingId == null || findingId.isBlank()) throw new IllegalArgumentException("findingId required");
        if (ruleId == null || ruleId.isBlank()) throw new IllegalArgumentException("ruleId required");
        Objects.requireNonNull(message, "message");
        taintTrace = List.copyOf(Objects.requireNonNull(taintTrace, "taintTrace"));
        sourceSlices = List.copyOf(Objects.requireNonNull(sourceSlices, "sourceSlices"));
    }
}
