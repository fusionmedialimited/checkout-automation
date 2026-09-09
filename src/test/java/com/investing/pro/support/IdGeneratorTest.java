package com.investing.pro.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdGeneratorTest {

    @Test
    void scenarioId_truncatesAVeryLongScenarioName() {
        String longName = "a".repeat(200);
        String id = IdGenerator.scenarioId(longName);

        // slug (<=60 chars) + "-" + an 8-char random suffix
        assertTrue(id.length() <= 69, "expected a bounded id, got length " + id.length() + ": " + id);
    }

    @Test
    void scenarioId_doesNotEndWithAHyphenBeforeTheSuffix_whenTruncated() {
        String longName = "word-".repeat(40);
        String id = IdGenerator.scenarioId(longName);

        assertFalse(id.matches(".*--[0-9a-f-]{8}$"), "truncation should not leave a double hyphen: " + id);
    }

    @Test
    void scenarioId_keepsShortNamesUntouched() {
        String id = IdGenerator.scenarioId("Landing page loads on master QA");
        assertTrue(id.startsWith("landing-page-loads-on-master-qa-"));
    }
}
