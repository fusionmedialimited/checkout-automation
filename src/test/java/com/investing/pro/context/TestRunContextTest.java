package com.investing.pro.context;

import com.investing.pro.model.CleanupStatus;
import com.investing.pro.model.SubscriptionRef;
import com.investing.pro.model.TestUserRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("TestRunContext: recording a resource again for the same id replaces it rather than duplicating it")
class TestRunContextTest {

    @Test
    @DisplayName("recordCreatedUser replaces the existing entry for the same userId instead of duplicating it")
    void recordCreatedUser_replacesExistingEntryForSameUserId() {
        TestRunContext context = new TestRunContext();
        TestUserRef original = new TestUserRef("user-1", "user-1@example.test", context.runId(), "scenario-1",
                Set.of(CleanupStatus.NOT_ATTEMPTED));
        context.recordCreatedUser(original);

        TestUserRef updated = original.withCleanupStatus(CleanupStatus.ACCOUNT_DELETED);
        context.recordCreatedUser(updated);

        assertEquals(1, context.createdUsers().size(), "updating an already-recorded user must not duplicate it");
        assertEquals(Set.of(CleanupStatus.ACCOUNT_DELETED), context.createdUsers().get(0).cleanupStatuses());
    }

    @Test
    @DisplayName("recordCreatedSubscription replaces the existing entry for the same subscriptionId instead of duplicating it")
    void recordCreatedSubscription_replacesExistingEntryForSameSubscriptionId() {
        TestRunContext context = new TestRunContext();
        SubscriptionRef original = new SubscriptionRef("sub-1", "user-1", "plan-1", context.runId(), "scenario-1",
                Set.of(CleanupStatus.NOT_ATTEMPTED));
        context.recordCreatedSubscription(original);

        SubscriptionRef updated = original.withCleanupStatus(CleanupStatus.SUBSCRIPTION_CANCELLED);
        context.recordCreatedSubscription(updated);

        assertEquals(1, context.createdSubscriptions().size(),
                "updating an already-recorded subscription must not duplicate it");
        assertEquals(Set.of(CleanupStatus.SUBSCRIPTION_CANCELLED),
                context.createdSubscriptions().get(0).cleanupStatuses());
    }

    @Test
    @DisplayName("recordCreatedUser keeps distinct users separate")
    void recordCreatedUser_keepsDistinctUsersSeparate() {
        TestRunContext context = new TestRunContext();
        context.recordCreatedUser(new TestUserRef("user-1", "user-1@example.test", context.runId(), "scenario-1", Set.of()));
        context.recordCreatedUser(new TestUserRef("user-2", "user-2@example.test", context.runId(), "scenario-1", Set.of()));

        assertEquals(2, context.createdUsers().size());
    }
}
