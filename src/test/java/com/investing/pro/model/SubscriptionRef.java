package com.investing.pro.model;

import java.util.LinkedHashSet;
import java.util.Set;

/** A record of a subscription created during a run, for reporting and eventual cleanup. */
public record SubscriptionRef(String subscriptionId, String userId, String planReference,
                               String runId, String scenarioId, Set<CleanupStatus> cleanupStatuses) {

    public SubscriptionRef {
        if (subscriptionId == null || subscriptionId.isBlank()) {
            throw new IllegalArgumentException("subscriptionId is required");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        cleanupStatuses = Set.copyOf(cleanupStatuses);
    }

    /**
     * Adds {@code newStatus} to the set of cleanup operations recorded against this
     * subscription. Additive, not a replacement: cancellation and a later refund are separate
     * facts that must both stay visible — see {@code docs/payment-safety.md} "Cleanup is not one
     * operation".
     */
    public SubscriptionRef withCleanupStatus(CleanupStatus newStatus) {
        Set<CleanupStatus> updated = new LinkedHashSet<>(cleanupStatuses);
        updated.add(newStatus);
        return new SubscriptionRef(subscriptionId, userId, planReference, runId, scenarioId, updated);
    }
}
