package com.investing.pro.config;

/** Controls when Playwright screenshots/traces are saved to {@code target/artifacts}. */
public enum ArtifactPolicy {
    ALWAYS,
    ON_FAILURE,
    NEVER;

    static ArtifactPolicy fromConfigValue(String value) {
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
        try {
            return ArtifactPolicy.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new ConfigValidationException(
                    "Invalid qa.artifactPolicy '" + value + "'; expected 'always', 'on-failure' or 'never'.");
        }
    }
}
