package com.investing.pro.model;

/**
 * Tracks what has actually been done to a resource a scenario created. Account deletion,
 * subscription cancellation, and refund are distinct operations — this enum intentionally does
 * not have a single "cleaned up" value that could imply one happened when it didn't.
 */
public enum CleanupStatus {
    NOT_ATTEMPTED,
    ACCOUNT_DELETED,
    SUBSCRIPTION_CANCELLED,
    REFUND_ISSUED,
    CLEANUP_FAILED
}
