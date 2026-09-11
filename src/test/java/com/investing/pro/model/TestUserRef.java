package com.investing.pro.model;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A record of a test user created during a run, kept so it can be reported and, where a
 * cleanup mechanism exists, cleaned up. {@code userId} is whatever identifier the provisioning
 * mechanism returns (no provisioning mechanism is implemented yet — see
 * docs/checkout-testing.md).
 */
public record TestUserRef(String userId, String email, String runId, String scenarioId,
                           Set<CleanupStatus> cleanupStatuses) {

    public TestUserRef {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        cleanupStatuses = Set.copyOf(cleanupStatuses);
    }

    /**
     * Adds {@code newStatus} to the set of cleanup operations recorded against this user.
     * Additive, not a replacement — see {@code SubscriptionRef.withCleanupStatus} and
     * {@code docs/payment-safety.md} "Cleanup is not one operation" for why a single value would
     * lose information (e.g. a failed attempt followed by a successful one).
     */
    public TestUserRef withCleanupStatus(CleanupStatus newStatus) {
        Set<CleanupStatus> updated = new LinkedHashSet<>(cleanupStatuses);
        updated.add(newStatus);
        return new TestUserRef(userId, email, runId, scenarioId, updated);
    }
}
