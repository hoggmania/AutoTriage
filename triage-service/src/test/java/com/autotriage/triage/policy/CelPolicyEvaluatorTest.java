package com.autotriage.triage.policy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CelPolicyEvaluatorTest {

    @Test
    void evaluatesThresholdPolicy() {
        CelPolicyEvaluator evaluator = new CelPolicyEvaluator();
        String policy = "confidencePercent <= 30 ? \"TRUE_POSITIVE\" : "
                + "confidencePercent <= 60 ? \"POTENTIAL_FALSE_POSITIVE\" : "
                + "\"FALSE_POSITIVE\"";

        assertEquals("TRUE_POSITIVE", evaluator.evaluate(policy, "CWE-079", 10));
        assertEquals("POTENTIAL_FALSE_POSITIVE", evaluator.evaluate(policy, "CWE-079", 55));
        assertEquals("FALSE_POSITIVE", evaluator.evaluate(policy, "CWE-079", 90));
    }
}
