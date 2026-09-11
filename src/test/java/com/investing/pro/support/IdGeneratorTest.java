package com.investing.pro.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("IdGenerator: bounded, collision-safe scenario ids for artifact filenames")
class IdGeneratorTest {

    @Test
    @DisplayName("scenarioId() truncates a very long scenario name to keep the id bounded")
    void scenarioId_truncatesAVeryLongScenarioName() {
        String longName = "a".repeat(200);
        String id = IdGenerator.scenarioId(longName);

        // slug (<=60 chars) + "-" + an 8-char random suffix
        assertTrue(id.length() <= 69, "expected a bounded id, got length " + id.length() + ": " + id);
    }

    @Test
    @DisplayName("scenarioId() never leaves a double hyphen where a truncated slug meets its random suffix")
    void scenarioId_doesNotEndWithAHyphenBeforeTheSuffix_whenTruncated() {
        String longName = "word-".repeat(40);
        String id = IdGenerator.scenarioId(longName);

        assertFalse(id.matches(".*--[0-9a-f-]{8}$"), "truncation should not leave a double hyphen: " + id);
    }

    @Test
    @DisplayName("scenarioId() keeps a short scenario name's slug intact before appending the random suffix")
    void scenarioId_keepsShortNamesUntouched() {
        String id = IdGenerator.scenarioId("Landing page loads on master QA");
        assertTrue(id.startsWith("landing-page-loads-on-master-qa-"));
    }
}
