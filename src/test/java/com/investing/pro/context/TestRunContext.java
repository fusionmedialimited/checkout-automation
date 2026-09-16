package com.investing.pro.context;

import com.investing.pro.model.SubscriptionRef;
import com.investing.pro.model.TestUserRef;
import com.investing.pro.support.IdGenerator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-scenario state, injected by PicoContainer so every scenario gets its own instance — the
 * cucumber-picocontainer dependency on the classpath registers PicoContainer as Cucumber's
 * object factory via {@code ServiceLoader} automatically, no {@code cucumber.properties} file is
 * involved. Holds identifiers and the resources a scenario creates, so they can be logged,
 * reported, and — once a provisioning integration exists — cleaned up without guessing what a
 * scenario actually did.
 */
public final class TestRunContext {

    private final String runId = IdGenerator.runId();
    private String scenarioId = "unset";
    // Keyed by userId/subscriptionId rather than a plain list: TestUserRef/SubscriptionRef are
    // immutable, so recording an updated status (e.g. via withCleanupStatus) means calling
    // recordCreatedUser/recordCreatedSubscription again with the new value for the same id — a
    // list would keep the old entry alongside the new one instead of replacing it.
    private final Map<String, TestUserRef> createdUsers = new LinkedHashMap<>();
    private final Map<String, SubscriptionRef> createdSubscriptions = new LinkedHashMap<>();

    public String runId() {
        return runId;
    }

    public String scenarioId() {
        return scenarioId;
    }

    public void startScenario(String scenarioName) {
        this.scenarioId = IdGenerator.scenarioId(scenarioName);
    }

    /** Inserts a newly created user, or replaces the existing entry for the same userId. */
    public void recordCreatedUser(TestUserRef user) {
        createdUsers.put(user.userId(), user);
    }

    /** Inserts a newly created subscription, or replaces the existing entry for the same subscriptionId. */
    public void recordCreatedSubscription(SubscriptionRef subscription) {
        createdSubscriptions.put(subscription.subscriptionId(), subscription);
    }

    public List<TestUserRef> createdUsers() {
        return List.copyOf(createdUsers.values());
    }

    public List<SubscriptionRef> createdSubscriptions() {
        return List.copyOf(createdSubscriptions.values());
    }
}
