package com.investing.pro.model;

/**
 * A record of a test user created during a run, kept so it can be reported and, where a
 * cleanup mechanism exists, cleaned up. {@code userId} is whatever identifier the provisioning
 * mechanism returns (no provisioning mechanism is implemented yet — see
 * docs/checkout-testing.md).
 */
public record TestUserRef(String userId, String email, String runId, String scenarioId,
                           CleanupStatus cleanupStatus) {

    public TestUserRef {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
    }

    public TestUserRef withCleanupStatus(CleanupStatus newStatus) {
        return new TestUserRef(userId, email, runId, scenarioId, newStatus);
    }
}
