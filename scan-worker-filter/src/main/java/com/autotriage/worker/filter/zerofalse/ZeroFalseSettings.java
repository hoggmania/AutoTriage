package com.autotriage.worker.filter.zerofalse;

import org.eclipse.microprofile.config.ConfigProvider;

public record ZeroFalseSettings(
        boolean enabled,
        int maxFindings,
        int maxTraceSteps,
        int contextLinesBefore,
        int contextLinesAfter) {

    private static final int UNLIMITED = Integer.MAX_VALUE;

    public static ZeroFalseSettings fromConfig() {
        var config = ConfigProvider.getConfig();
        boolean enabled = config.getOptionalValue("zerofalse.enabled", Boolean.class).orElse(false);
        int maxFindings = normalizeLimit(config.getOptionalValue("zerofalse.max-findings", Integer.class).orElse(25));
        int maxTraceSteps = normalizeLimit(config.getOptionalValue("zerofalse.max-trace-steps", Integer.class).orElse(20));
        int before = Math.max(0, config.getOptionalValue("zerofalse.context.lines-before", Integer.class).orElse(2));
        int after = Math.max(0, config.getOptionalValue("zerofalse.context.lines-after", Integer.class).orElse(2));
        return new ZeroFalseSettings(enabled, maxFindings, maxTraceSteps, before, after);
    }

    private static int normalizeLimit(int value) {
        if (value < 0) {
            return UNLIMITED;
        }
        return value;
    }
}
