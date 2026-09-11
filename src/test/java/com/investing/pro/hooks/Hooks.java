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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Scenario lifecycle: a fresh browser context per scenario, plus artifact capture. Cucumber
 * creates one instance of this class per scenario (via cucumber-picocontainer), so
 * {@link BrowserResources} and {@link TestRunContext} injected here are already scenario-scoped
 * — no manual setup/teardown bookkeeping is needed beyond what's below.
 */
public final class Hooks {

    private static final Logger LOG = LoggerFactory.getLogger(Hooks.class);
    private static final Path ARTIFACT_ROOT = Path.of("build", "artifacts");

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
        String artifactName = testRunContext.runId() + "_" + testRunContext.scenarioId();
        // Declared false and only ever set true inside the try: if Config.artifactPolicy()
        // itself throws (e.g. an invalid override value), browser cleanup below must still run
        // rather than being skipped by an exception thrown before entering the try/finally.
        boolean shouldCapture = false;

        try {
            ArtifactPolicy policy = Config.artifactPolicy();
            shouldCapture = policy == ArtifactPolicy.ALWAYS
                    || (policy == ArtifactPolicy.ON_FAILURE && scenario.isFailed());
            captureTracingAndScreenshot(scenario, shouldCapture, artifactName);
        } catch (RuntimeException e) {
            // Artifact capture must never mask the scenario's own pass/fail result. Pass the
            // exception itself (not just its message) so the stack trace survives in the logs.
            LOG.warn("Artifact capture failed for scenario '{}'", scenario.getName(), e);
        } finally {
            try {
                // Only the context closes here — video still needs a live browser/driver
                // connection to save/delete, which browserResources.close() below tears down.
                browserResources.closeContext();
            } catch (RuntimeException e) {
                LOG.warn("Browser context cleanup failed for scenario '{}'", scenario.getName(), e);
            }
            try {
                captureVideoIfNeeded(scenario, shouldCapture, artifactName);
            } catch (RuntimeException e) {
                LOG.warn("Video capture failed for scenario '{}'", scenario.getName(), e);
            } finally {
                try {
                    browserResources.close();
                } catch (RuntimeException e) {
                    // Same reasoning as above: cleanup failing must never override the
                    // scenario's own result.
                    LOG.warn("Browser cleanup failed for scenario '{}'", scenario.getName(), e);
                }
            }
        }
    }

    private void captureTracingAndScreenshot(Scenario scenario, boolean shouldCapture, String artifactName) {
        browserResources.finishTracing(shouldCapture, ARTIFACT_ROOT.resolve("traces").resolve(artifactName + ".zip"));

        // browserResources.page() is null if @Before failed before creating a page (e.g. the
        // browser itself failed to launch); there is nothing to screenshot in that case.
        if (shouldCapture && browserResources.page() != null) {
            Path screenshotPath = ARTIFACT_ROOT.resolve("screenshots").resolve(artifactName + ".png");
            browserResources.captureScreenshot(screenshotPath);
            // Attaching the actual bytes (not just a path note) is what makes the screenshot show
            // up in the Allure report's result data — SmokeTestRunner registers only pretty,
            // rerun, and the Allure plugin (no Cucumber html:/json: output), so Allure is the
            // only formatter that reads this attachment.
            try {
                scenario.attach(Files.readAllBytes(screenshotPath), "image/png", artifactName);
            } catch (IOException e) {
                LOG.warn("Failed to attach screenshot for scenario '{}'", scenario.getName(), e);
            }
        }
    }

    private void captureVideoIfNeeded(Scenario scenario, boolean shouldCapture, String artifactName) {
        Path videoPath = ARTIFACT_ROOT.resolve("videos").resolve(artifactName + ".webm");
        browserResources.finishVideo(shouldCapture, videoPath);

        // finishVideo() is a no-op (no file written) if @Before failed before creating a page —
        // same case captureTracingAndScreenshot() guards against above. Without this check, that
        // case logs a misleading "failed to attach" warning that obscures the real failure.
        if (shouldCapture && Files.exists(videoPath)) {
            try {
                scenario.attach(Files.readAllBytes(videoPath), "video/webm", artifactName);
            } catch (IOException e) {
                LOG.warn("Failed to attach video for scenario '{}'", scenario.getName(), e);
            }
        }
    }
}
