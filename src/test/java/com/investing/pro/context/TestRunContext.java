package com.investing.pro.context;

import com.investing.pro.model.SubscriptionRef;
import com.investing.pro.model.TestUserRef;
import com.investing.pro.support.IdGenerator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private final List<TestUserRef> createdUsers = new ArrayList<>();
    private final List<SubscriptionRef> createdSubscriptions = new ArrayList<>();

    public String runId() {
        return runId;
    }

    public String scenarioId() {
        return scenarioId;
    }

    public void startScenario(String scenarioName) {
        this.scenarioId = IdGenerator.scenarioId(scenarioName);
    }

    public void recordCreatedUser(TestUserRef user) {
        createdUsers.add(user);
    }

    public void recordCreatedSubscription(SubscriptionRef subscription) {
        createdSubscriptions.add(subscription);
    }

    public List<TestUserRef> createdUsers() {
        return Collections.unmodifiableList(createdUsers);
    }

    public List<SubscriptionRef> createdSubscriptions() {
        return Collections.unmodifiableList(createdSubscriptions);
    }
}
