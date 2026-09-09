package com.investing.pro.model;

/** A record of a subscription created during a run, for reporting and eventual cleanup. */
public record SubscriptionRef(String subscriptionId, String userId, String planReference,
                               String runId, String scenarioId, CleanupStatus cleanupStatus) {

    public SubscriptionRef {
        if (subscriptionId == null || subscriptionId.isBlank()) {
            throw new IllegalArgumentException("subscriptionId is required");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
    }

    public SubscriptionRef withCleanupStatus(CleanupStatus newStatus) {
        return new SubscriptionRef(subscriptionId, userId, planReference, runId, scenarioId, newStatus);
    }
}
