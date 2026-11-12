package com.autotriage.common.remediation;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class BoundedContextBuilderTest {
    @Test void respectsBudgetAndIncludesEvidence() {
        FindingContext f = new FindingContext("f1", "java.sql.injection", "source reaches sink",
            List.of("request parameter", "SQL query"), List.of(new SourceSlice("src/A.java", 12, "String sql = input;")));
        String rendered = BoundedContextBuilder.render(f, 256);
        assertTrue(rendered.contains("f1"));
        assertTrue(rendered.contains("java.sql.injection"));
        assertTrue(rendered.length() <= 256);
    }
    @Test void truncatesLongUntrustedInput() {
        FindingContext f = new FindingContext("f1", "r1", "x".repeat(10000), List.of(), List.of());
        assertEquals(256, BoundedContextBuilder.render(f, 256).length());
    }
    @Test void rejectsPathTraversal() {
        assertThrows(IllegalArgumentException.class, () -> new SourceSlice("../secret", 1, "x"));
    }
    @Test void rejectsSmallBudget() {
        FindingContext f = new FindingContext("f1", "r1", "x", List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> BoundedContextBuilder.render(f, 10));
    }
}
