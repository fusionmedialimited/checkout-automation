package com.investing.pro.support;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/** Generates identifiers used to isolate and trace test data across a run and its scenarios. */
public final class IdGenerator {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    // Scenario names feed straight into artifact file names (<runId>_<scenarioId>.png/zip); an
    // unbounded slug from a long scenario title risks common filesystem path-length limits.
    private static final int MAX_SLUG_LENGTH = 60;

    // Computed once and cached: every scenario in this JVM must share the same run id (a new
    // TestRunContext, and therefore a fresh call to runId(), is created per scenario), otherwise
    // two scenarios in the same local run would get different timestamp-based ids.
    private static final String RUN_ID = computeRunId();

    private IdGenerator() {
    }

    /** One run id per JVM, reusing CI's own run identifier when available. */
    public static String runId() {
        return RUN_ID;
    }

    private static String computeRunId() {
        String ci = System.getenv("GITHUB_RUN_ID");
        if (ci != null && !ci.isBlank()) {
            return "gha-" + ci;
        }
        return "local-" + TIMESTAMP.format(Instant.now().atZone(java.time.ZoneOffset.UTC));
    }

    public static String scenarioId(String scenarioName) {
        String slug = scenarioName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.length() > MAX_SLUG_LENGTH) {
            slug = slug.substring(0, MAX_SLUG_LENGTH).replaceAll("-$", "");
        }
        String shortSuffix = UUID.randomUUID().toString().substring(0, 8);
        return (slug.isBlank() ? "scenario" : slug) + "-" + shortSuffix;
    }
}
