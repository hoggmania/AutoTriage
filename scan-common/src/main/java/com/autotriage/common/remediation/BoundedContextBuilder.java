package com.autotriage.common.remediation;
import java.util.Objects;
public final class BoundedContextBuilder {
    private BoundedContextBuilder() {}
    public static String render(FindingContext finding, int budget) {
        Objects.requireNonNull(finding);
        if (budget < 256) throw new IllegalArgumentException("budget too small");
        StringBuilder out = new StringBuilder();
        append(out, "Finding: " + finding.findingId() + "\nRule: " + finding.ruleId() + "\nMessage: " + finding.message() + "\n", budget);
        for (String trace : finding.taintTrace()) append(out, "Taint: " + trace + "\n", budget);
        for (SourceSlice slice : finding.sourceSlices()) {
            append(out, "Source: " + slice.path() + ":" + slice.startLine() + "\n", budget);
            append(out, slice.content() + "\n", budget);
        }
        return out.toString();
    }
    private static void append(StringBuilder out, String value, int budget) {
        int n = Math.min(value.length(), budget - out.length());
        if (n > 0) out.append(value, 0, n);
    }
}
