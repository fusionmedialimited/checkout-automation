package com.investing.pro.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SubscriptionRef: additive cleanup-status history with a self-clearing placeholder")
class SubscriptionRefTest {

    private SubscriptionRef newRef(CleanupStatus... initial) {
        return new SubscriptionRef("sub-1", "user-1", "plan-1", "run-1", "scenario-1", Set.of(initial));
    }

    @Test
    @DisplayName("constructor rejects a blank subscriptionId")
    void constructor_rejectsBlankSubscriptionId() {
        assertThrows(IllegalArgumentException.class,
                () -> new SubscriptionRef(" ", "user-1", "plan-1", "run-1", "scenario-1", Set.of()));
    }

    @Test
    @DisplayName("constructor rejects a blank userId")
    void constructor_rejectsBlankUserId() {
        assertThrows(IllegalArgumentException.class,
                () -> new SubscriptionRef("sub-1", " ", "plan-1", "run-1", "scenario-1", Set.of()));
    }

    @Test
    @DisplayName("withCleanupStatus removes the NOT_ATTEMPTED placeholder once a real status is recorded")
    void withCleanupStatus_removesNotAttemptedPlaceholder() {
        SubscriptionRef ref = newRef(CleanupStatus.NOT_ATTEMPTED);

        SubscriptionRef cancelled = ref.withCleanupStatus(CleanupStatus.SUBSCRIPTION_CANCELLED);

        assertEquals(Set.of(CleanupStatus.SUBSCRIPTION_CANCELLED), cancelled.cleanupStatuses());
        assertFalse(cancelled.cleanupStatuses().contains(CleanupStatus.NOT_ATTEMPTED));
    }

    @Test
    @DisplayName("withCleanupStatus is additive: a later refund keeps the earlier cancellation visible")
    void withCleanupStatus_isAdditive_cancellationThenRefund() {
        SubscriptionRef cancelled = newRef(CleanupStatus.NOT_ATTEMPTED)
                .withCleanupStatus(CleanupStatus.SUBSCRIPTION_CANCELLED);

        SubscriptionRef refunded = cancelled.withCleanupStatus(CleanupStatus.REFUND_ISSUED);

        assertTrue(refunded.cleanupStatuses().contains(CleanupStatus.SUBSCRIPTION_CANCELLED),
                "cancellation fact must still be present after the refund is recorded");
        assertTrue(refunded.cleanupStatuses().contains(CleanupStatus.REFUND_ISSUED));
    }

    @Test
    @DisplayName("withCleanupStatus keeps a prior CLEANUP_FAILED fact even after a later success")
    void withCleanupStatus_keepsFailureHistory_afterLaterSuccess() {
        SubscriptionRef failedThenSucceeded = newRef(CleanupStatus.CLEANUP_FAILED)
                .withCleanupStatus(CleanupStatus.SUBSCRIPTION_CANCELLED);

        assertTrue(failedThenSucceeded.cleanupStatuses().contains(CleanupStatus.CLEANUP_FAILED),
                "a prior failed attempt is a fact worth keeping, unlike the NOT_ATTEMPTED placeholder");
        assertTrue(failedThenSucceeded.cleanupStatuses().contains(CleanupStatus.SUBSCRIPTION_CANCELLED));
    }
}
