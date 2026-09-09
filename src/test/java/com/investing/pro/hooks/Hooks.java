package com.investing.pro.hooks;

import com.investing.pro.config.ArtifactPolicy;
import com.investing.pro.config.Config;
import com.investing.pro.context.BrowserResources;
import com.investing.pro.context.TestRunContext;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Scenario lifecycle: a fresh browser context per scenario, plus artifact capture. Cucumber
 * creates one instance of this class per scenario (via cucumber-picocontainer), so
 * {@link BrowserResources} and {@link TestRunContext} injected here are already scenario-scoped
 * — no manual setup/teardown bookkeeping is needed beyond what's below.
 */
public final class Hooks {

    private static final Logger LOG = LoggerFactory.getLogger(Hooks.class);
    private static final Path ARTIFACT_ROOT = Path.of("target", "artifacts");

    private final BrowserResources browserResources;
    private final TestRunContext testRunContext;

    public Hooks(BrowserResources browserResources, TestRunContext testRunContext) {
        this.browserResources = browserResources;
        this.testRunContext = testRunContext;
    }

    @Before
    public void setUp(Scenario scenario) {
        testRunContext.startScenario(scenario.getName());
        LOG.info("Starting scenario '{}' [run={}, scenario={}]", scenario.getName(),
                testRunContext.runId(), testRunContext.scenarioId());
        browserResources.open();
    }

    @After
    public void tearDown(Scenario scenario) {
        try {
            captureArtifactsIfNeeded(scenario);
        } catch (RuntimeException e) {
            // Artifact capture must never mask the scenario's own pass/fail result.
            LOG.warn("Artifact capture failed for scenario '{}': {}", scenario.getName(), e.getMessage());
        } finally {
            browserResources.close();
        }
    }

    private void captureArtifactsIfNeeded(Scenario scenario) {
        ArtifactPolicy policy = Config.artifactPolicy();
        boolean shouldCapture = policy == ArtifactPolicy.ALWAYS
                || (policy == ArtifactPolicy.ON_FAILURE && scenario.isFailed());

        String artifactName = testRunContext.runId() + "_" + testRunContext.scenarioId();
        browserResources.finishTracing(shouldCapture, ARTIFACT_ROOT.resolve("traces").resolve(artifactName + ".zip"));

        if (shouldCapture) {
            Path screenshotPath = ARTIFACT_ROOT.resolve("screenshots").resolve(artifactName + ".png");
            browserResources.captureScreenshot(screenshotPath);
            scenario.attach("Artifacts saved under " + screenshotPath.getParent(), "text/plain", artifactName);
        }
    }
}
