package com.autotriage.worker.opengrep;

import com.autotriage.common.engine.AnalysisEngine;
import com.autotriage.common.engine.EngineDescriptor;
import com.autotriage.common.engine.EngineRegistry;
import com.autotriage.common.engine.EngineRequest;
import com.autotriage.common.engine.EngineResult;
import com.autotriage.common.evidence.Evidence;
import com.autotriage.common.evidence.EvidenceAssessment;
import com.autotriage.common.evidence.EvidenceCalibrator;
import com.autotriage.common.evidence.EvidenceLevel;
import com.autotriage.common.evidence.EvidenceProvenance;
import com.autotriage.common.evidence.FindingSeverity;
import com.autotriage.common.model.ArtifactRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineEvidenceSpiContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void descriptorHasStableIdentityAndDeterministicSerialization() throws Exception {
        EngineDescriptor descriptor = new EngineDescriptor(
                "opengrep", "1.2.3", Set.of("source-archive", "git-tree"), Set.of("sarif-raw"));

        String first = mapper.writeValueAsString(descriptor);
        String second = mapper.writeValueAsString(new EngineDescriptor(
                "opengrep", "1.2.3", Set.of("git-tree", "source-archive"), Set.of("sarif-raw")));

        assertEquals(first, second);
        assertEquals(descriptor, mapper.readValue(first, EngineDescriptor.class));
        assertEquals(List.of("git-tree", "source-archive"), new ArrayList<>(descriptor.getInputKinds()));
        assertThrows(UnsupportedOperationException.class, () -> descriptor.getInputKinds().add("directory"));
    }

    @Test
    void requestAndResultDefensivelyCopyMutableCollections() throws Exception {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("ruleset", "default");
        EngineRequest request = new EngineRequest(
                "opengrep", "run-1", new ArtifactRef("file:///source.tar.gz", "source-archive"), parameters);
        parameters.put("ruleset", "changed");

        Evidence evidence = evidence();
        List<ArtifactRef> outputs = new ArrayList<>();
        outputs.add(new ArtifactRef("file:///raw.sarif", "sarif-raw"));
        List<Evidence> evidenceItems = new ArrayList<>();
        evidenceItems.add(evidence);
        EngineResult result = new EngineResult("opengrep", "1.2.3", outputs, evidenceItems);
        outputs.clear();
        evidenceItems.clear();

        assertEquals("default", request.getParameters().get("ruleset"));
        assertEquals(1, result.getOutputs().size());
        assertEquals(1, result.getEvidence().size());
        assertThrows(UnsupportedOperationException.class, () -> request.getParameters().put("x", "y"));
        assertThrows(UnsupportedOperationException.class, () -> result.getOutputs().clear());
        assertEquals(request, mapper.readValue(mapper.writeValueAsString(request), EngineRequest.class));
        assertEquals(result, mapper.readValue(mapper.writeValueAsString(result), EngineResult.class));
    }

    @Test
    void evidenceIsImmutableAndSerializationIsDeterministic() throws Exception {
        Evidence evidence = evidence();

        String json = mapper.writeValueAsString(evidence);

        assertEquals(evidence, mapper.readValue(json, Evidence.class));
        assertEquals(
                "{\"id\":\"finding-1\",\"type\":\"static-analysis-finding\",\"level\":\"HIGH\","
                        + "\"summary\":\"SQL injection data flow\",\"provenance\":{\"engineId\":\"opengrep\","
                        + "\"engineVersion\":\"1.2.3\",\"source\":\"rules/sql-injection.yml\","
                        + "\"details\":{\"ruleId\":\"java.sql-injection\"}},"
                        + "\"attributes\":{\"cwe\":\"CWE-89\",\"path\":\"src/Example.java\"}}",
                json);
        assertThrows(UnsupportedOperationException.class,
                () -> evidence.getAttributes().put("line", "12"));
    }

    @Test
    void calibrationIsSeparateFromEngineExecution() {
        EvidenceCalibrator calibrator = evidence -> new EvidenceAssessment(
                evidence.getId(), EvidenceLevel.STRONG, 0.95, "confirmed by reachable data flow");

        EvidenceAssessment assessment = calibrator.calibrate(evidence());

        assertEquals("finding-1", assessment.getEvidenceId());
        assertEquals(EvidenceLevel.STRONG, assessment.getLevel());
        assertEquals(0.95, assessment.getConfidence());
        assertThrows(IllegalArgumentException.class,
                () -> new EvidenceAssessment("finding-1", EvidenceLevel.MODERATE, 1.01, "invalid"));
    }

    @Test
    void registryRejectsUnknownAndDuplicateEnginesBeforeExecution() {
        StubEngine engine = new StubEngine();
        EngineRegistry registry = new EngineRegistry(List.of(engine));
        EngineRequest request = new EngineRequest(
                "missing", "run-1", new ArtifactRef("file:///source.tar.gz", "source-archive"), Map.of());

        assertEquals(List.of("opengrep"), new ArrayList<>(registry.getEngineIds()));
        assertSame(engine, registry.require("opengrep"));
        assertThrows(IllegalArgumentException.class, () -> registry.execute(request));
        assertEquals(0, engine.executions);
        assertThrows(IllegalArgumentException.class, () -> new EngineRegistry(List.of(engine, new StubEngine())));
    }

    @Test
    void registryEnforcesDeclaredInputsOutputsAndEngineIdentity() {
        StubEngine engine = new StubEngine();
        EngineRegistry registry = new EngineRegistry(List.of(engine));

        EngineResult result = registry.execute(new EngineRequest(
                "opengrep", "run-1", new ArtifactRef("file:///source.tar.gz", "source-archive"), Map.of()));

        assertEquals(1, engine.executions);
        assertEquals("sarif-raw", result.getOutputs().get(0).getKind());
        assertThrows(IllegalArgumentException.class, () -> registry.execute(new EngineRequest(
                "opengrep", "run-2", new ArtifactRef("file:///source", "directory"), Map.of())));
        assertEquals(1, engine.executions);

        AnalysisEngine badOutput = new AnalysisEngine() {
            @Override
            public EngineDescriptor descriptor() {
                return new EngineDescriptor("bad", "1", Set.of("source-archive"), Set.of("sarif-raw"));
            }

            @Override
            public EngineResult analyze(EngineRequest request) {
                return new EngineResult("bad", "1", List.of(new ArtifactRef("file:///x", "json")), List.of());
            }
        };
        assertThrows(IllegalStateException.class, () -> new EngineRegistry(List.of(badOutput)).execute(
                new EngineRequest("bad", "run-3", requestSource(), Map.of())));
    }

    @Test
    void blankRequiredValuesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new EngineDescriptor(" ", "1", Set.of("source"), Set.of("result")));
        assertThrows(IllegalArgumentException.class,
                () -> new EngineRequest("opengrep", "", requestSource(), Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new EvidenceProvenance("opengrep", "1", "", Map.of()));
        assertTrue(FindingSeverity.valueOf("INFORMATIONAL") == FindingSeverity.INFORMATIONAL);
    }

    private static ArtifactRef requestSource() {
        return new ArtifactRef("file:///source.tar.gz", "source-archive");
    }

    private static Evidence evidence() {
        return new Evidence(
                "finding-1",
                "static-analysis-finding",
                FindingSeverity.HIGH,
                "SQL injection data flow",
                new EvidenceProvenance(
                        "opengrep", "1.2.3", "rules/sql-injection.yml",
                        Map.of("ruleId", "java.sql-injection")),
                Map.of("path", "src/Example.java", "cwe", "CWE-89"));
    }

    private static final class StubEngine implements AnalysisEngine {
        private int executions;

        @Override
        public EngineDescriptor descriptor() {
            return new EngineDescriptor("opengrep", "1.2.3", Set.of("source-archive"), Set.of("sarif-raw"));
        }

        @Override
        public EngineResult analyze(EngineRequest request) {
            executions++;
            return new EngineResult(
                    "opengrep", "1.2.3",
                    List.of(new ArtifactRef("file:///raw.sarif", "sarif-raw")), List.of());
        }
    }
}
