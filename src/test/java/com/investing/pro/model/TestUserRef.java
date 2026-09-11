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
     * {@link CleanupStatus#NOT_ATTEMPTED} is removed here rather than accumulated: it's a pure
     * placeholder, not a historical fact worth keeping once a real status exists.
     *
     * @throws IllegalArgumentException if {@code newStatus} is {@link CleanupStatus#NOT_ATTEMPTED}
     *         — it's only ever a valid initial state (set via the constructor), never something
     *         to record after the fact; recording it here would reintroduce the placeholder
     *         alongside whatever real cleanup facts already exist.
     */
    public TestUserRef withCleanupStatus(CleanupStatus newStatus) {
        if (newStatus == CleanupStatus.NOT_ATTEMPTED) {
            throw new IllegalArgumentException(
                    "NOT_ATTEMPTED is only a valid initial cleanupStatuses value, not something to record via withCleanupStatus");
        }
        Set<CleanupStatus> updated = new LinkedHashSet<>(cleanupStatuses);
        updated.remove(CleanupStatus.NOT_ATTEMPTED);
        updated.add(newStatus);
        return new TestUserRef(userId, email, runId, scenarioId, updated);
    }
}
