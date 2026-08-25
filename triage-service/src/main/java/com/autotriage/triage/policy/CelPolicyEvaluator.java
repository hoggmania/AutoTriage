package com.autotriage.triage.policy;

import com.autotriage.common.evidence.TriageEvidence;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.types.SimpleType;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class CelPolicyEvaluator {
    private static final Logger log = Logger.getLogger(CelPolicyEvaluator.class);
    private final CelCompiler compiler;
    private final CelRuntime runtime;
    private final Map<String, CelRuntime.Program> programCache = new ConcurrentHashMap<>();

    public CelPolicyEvaluator() {
        compiler = CelCompilerFactory.standardCelCompilerBuilder()
                .addVar("cweId", SimpleType.STRING)
                .addVar("evidence", SimpleType.DYN)
                .addVar("provenance", SimpleType.DYN)
                .build();
        runtime = CelRuntimeFactory.standardCelRuntimeBuilder().build();
    }

    public String evaluate(String policy, String cweId, TriageEvidence triageEvidence) {
        if (policy == null || policy.isBlank() || triageEvidence == null) return null;
        try {
            CelRuntime.Program program = programCache.computeIfAbsent(policy, this::compileProgram);
            if (program == null) return null;
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("level", triageEvidence.getCalibratedLevel().name());
            evidence.put("calibratorId", triageEvidence.getCalibration().getCalibratorId());
            evidence.put("calibrationVersion", triageEvidence.getCalibration().getVersion());
            evidence.put("calibrationProfile", triageEvidence.getCalibration().getProfile());
            evidence.put("rawScore", triageEvidence.getCalibration().getRawScore());
            var p = triageEvidence.getProvenance();
            Map<String, Object> provenance = new LinkedHashMap<>();
            provenance.put("engineId", p.getEngineId());
            provenance.put("engineVersion", p.getEngineVersion());
            provenance.put("provider", value(p.getProvider()));
            provenance.put("model", value(p.getModel()));
            provenance.put("modelVersion", value(p.getModelVersion()));
            provenance.put("promptVariant", value(p.getPromptVariant()));
            provenance.put("promptHash", value(p.getPromptHash()));
            provenance.put("sourceHash", value(p.getSourceHash()));
            provenance.put("sarifHash", value(p.getSarifHash()));
            Object result = program.eval(Map.of(
                    "cweId", cweId == null ? "" : cweId,
                    "evidence", evidence,
                    "provenance", provenance));
            return result == null ? null : result.toString();
        } catch (Exception e) {
            log.warnv("CEL evaluation failed: {0}", e.getMessage());
            return null;
        }
    }

    private CelRuntime.Program compileProgram(String policy) {
        try {
            CelAbstractSyntaxTree ast = compiler.compile(policy).getAst();
            return runtime.createProgram(ast);
        } catch (Exception e) {
            log.warnv("Failed to compile CEL policy: {0}", e.getMessage());
            return null;
        }
    }

    private static String value(String value) { return value == null ? "" : value; }
}
