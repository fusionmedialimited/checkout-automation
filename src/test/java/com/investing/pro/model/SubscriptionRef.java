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
        if (cleanupStatuses.contains(CleanupStatus.NOT_ATTEMPTED) && cleanupStatuses.size() > 1) {
            throw new IllegalArgumentException(
                    "NOT_ATTEMPTED cannot coexist with any other cleanup status: " + cleanupStatuses);
        }
    }

    /**
     * Adds {@code newStatus} to the set of cleanup operations recorded against this
     * subscription. Additive, not a replacement: cancellation and a later refund are separate
     * facts that must both stay visible — see {@code docs/payment-safety.md} "Cleanup is not one
     * operation". {@link CleanupStatus#NOT_ATTEMPTED} is a pure placeholder (nothing has
     * happened yet), not a historical fact worth keeping once something actually has, so it's
     * removed here rather than accumulated alongside a real status — unlike
     * {@link CleanupStatus#CLEANUP_FAILED}, which does stay even after a later success, since a
     * prior failed attempt is itself a fact worth keeping visible.
     *
     * @throws IllegalArgumentException if {@code newStatus} is {@link CleanupStatus#NOT_ATTEMPTED}
     *         — it's only ever a valid initial state (set via the constructor), never something
     *         to record after the fact; recording it here would reintroduce the placeholder
     *         alongside whatever real cleanup facts already exist.
     */
    public SubscriptionRef withCleanupStatus(CleanupStatus newStatus) {
        if (newStatus == CleanupStatus.NOT_ATTEMPTED) {
            throw new IllegalArgumentException(
                    "NOT_ATTEMPTED is only a valid initial cleanupStatuses value, not something to record via withCleanupStatus");
        }
        Set<CleanupStatus> updated = new LinkedHashSet<>(cleanupStatuses);
        updated.remove(CleanupStatus.NOT_ATTEMPTED);
        updated.add(newStatus);
        return new SubscriptionRef(subscriptionId, userId, planReference, runId, scenarioId, updated);
    }
}
