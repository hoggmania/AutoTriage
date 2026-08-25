package com.autotriage.common.engine;

public interface AnalysisEngine {
    EngineDescriptor descriptor();

    EngineResult analyze(EngineRequest request);
}
