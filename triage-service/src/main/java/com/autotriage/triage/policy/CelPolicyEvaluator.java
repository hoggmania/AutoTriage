package com.autotriage.triage.policy;

import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.types.SimpleType;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

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
                .addVar("confidencePercent", SimpleType.INT)
                .build();
        runtime = CelRuntimeFactory.standardCelRuntimeBuilder().build();
    }

    public String evaluate(String policy, String cweId, int confidencePercent) {
        if (policy == null || policy.isBlank()) {
            return null;
        }
        try {
            CelRuntime.Program program = programCache.computeIfAbsent(policy, this::compileProgram);
            if (program == null) {
                return null;
            }
            Object result = program.eval(Map.of(
                    "cweId", cweId == null ? "" : cweId,
                    "confidencePercent", confidencePercent));
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
}
