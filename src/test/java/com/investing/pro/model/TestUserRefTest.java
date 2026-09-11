package com.investing.pro.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TestUserRef: additive cleanup-status history with a self-clearing placeholder")
class TestUserRefTest {

    private TestUserRef newRef(CleanupStatus... initial) {
        return new TestUserRef("user-1", "user-1@example.test", "run-1", "scenario-1", Set.of(initial));
    }

    @Test
    @DisplayName("constructor rejects a blank userId")
    void constructor_rejectsBlankUserId() {
        assertThrows(IllegalArgumentException.class,
                () -> new TestUserRef(" ", "user-1@example.test", "run-1", "scenario-1", Set.of()));
    }

    @Test
    @DisplayName("constructor rejects a blank email")
    void constructor_rejectsBlankEmail() {
        assertThrows(IllegalArgumentException.class,
                () -> new TestUserRef("user-1", " ", "run-1", "scenario-1", Set.of()));
    }

    @Test
    @DisplayName("withCleanupStatus removes the NOT_ATTEMPTED placeholder once a real status is recorded")
    void withCleanupStatus_removesNotAttemptedPlaceholder() {
        TestUserRef ref = newRef(CleanupStatus.NOT_ATTEMPTED);

        TestUserRef deleted = ref.withCleanupStatus(CleanupStatus.ACCOUNT_DELETED);

        assertEquals(Set.of(CleanupStatus.ACCOUNT_DELETED), deleted.cleanupStatuses());
        assertFalse(deleted.cleanupStatuses().contains(CleanupStatus.NOT_ATTEMPTED));
    }

    @Test
    @DisplayName("withCleanupStatus keeps a prior CLEANUP_FAILED fact even after a later success")
    void withCleanupStatus_keepsFailureHistory_afterLaterSuccess() {
        TestUserRef failedThenSucceeded = newRef(CleanupStatus.CLEANUP_FAILED)
                .withCleanupStatus(CleanupStatus.ACCOUNT_DELETED);

        assertTrue(failedThenSucceeded.cleanupStatuses().contains(CleanupStatus.CLEANUP_FAILED),
                "a prior failed attempt is a fact worth keeping, unlike the NOT_ATTEMPTED placeholder");
        assertTrue(failedThenSucceeded.cleanupStatuses().contains(CleanupStatus.ACCOUNT_DELETED));
    }

    @Test
    @DisplayName("withCleanupStatus rejects NOT_ATTEMPTED, which would reintroduce the placeholder alongside a real status")
    void withCleanupStatus_rejectsNotAttempted() {
        TestUserRef deleted = newRef(CleanupStatus.NOT_ATTEMPTED).withCleanupStatus(CleanupStatus.ACCOUNT_DELETED);

        assertThrows(IllegalArgumentException.class, () -> deleted.withCleanupStatus(CleanupStatus.NOT_ATTEMPTED));
    }
}
