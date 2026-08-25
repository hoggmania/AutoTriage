package com.autotriage.common.engine;

import com.autotriage.common.model.ArtifactRef;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public final class EngineRegistry {
    private final Map<String, AnalysisEngine> engines;

    public EngineRegistry(Collection<? extends AnalysisEngine> engines) {
        Objects.requireNonNull(engines, "engines");
        TreeMap<String, AnalysisEngine> registered = new TreeMap<>();
        for (AnalysisEngine engine : engines) {
            Objects.requireNonNull(engine, "engine");
            EngineDescriptor descriptor = Objects.requireNonNull(engine.descriptor(), "engine descriptor");
            if (registered.putIfAbsent(descriptor.getId(), engine) != null) {
                throw new IllegalArgumentException("Duplicate engine id: " + descriptor.getId());
            }
        }
        this.engines = Collections.unmodifiableMap(registered);
    }

    public Set<String> getEngineIds() {
        return engines.keySet();
    }

    public AnalysisEngine require(String engineId) {
        AnalysisEngine engine = engines.get(engineId);
        if (engine == null) throw new IllegalArgumentException("Unknown engine id: " + engineId);
        return engine;
    }

    public EngineResult execute(EngineRequest request) {
        Objects.requireNonNull(request, "request");
        AnalysisEngine engine = require(request.getEngineId());
        EngineDescriptor descriptor = engine.descriptor();
        String inputKind = request.getSource().getKind();
        if (!descriptor.getInputKinds().contains(inputKind)) {
            throw new IllegalArgumentException(
                    "Engine " + descriptor.getId() + " does not accept input kind: " + inputKind);
        }
        EngineResult result = Objects.requireNonNull(engine.analyze(request), "engine result");
        if (!descriptor.getId().equals(result.getEngineId())
                || !descriptor.getVersion().equals(result.getEngineVersion())) {
            throw new IllegalStateException("Engine result identity does not match its descriptor");
        }
        for (ArtifactRef output : result.getOutputs()) {
            if (!descriptor.getOutputKinds().contains(output.getKind())) {
                throw new IllegalStateException(
                        "Engine " + descriptor.getId() + " returned undeclared output kind: " + output.getKind());
            }
        }
        return result;
    }
}
