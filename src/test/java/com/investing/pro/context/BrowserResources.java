package com.investing.pro.context;

import com.investing.pro.config.ArtifactPolicy;
import com.investing.pro.config.Config;
import com.investing.pro.config.ConfigValidationException;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.assertions.PlaywrightAssertions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Owns one Playwright instance, browser, context and page for a single scenario. Injected by
 * PicoContainer so it is scenario-scoped: nothing here is static or shared across scenarios, and
 * every Playwright object stays on the thread that created it, per the framework's browser
 * lifecycle rules (see docs/architecture.md). Sequential execution only — this class is not
 * thread-safe by design.
 */
public final class BrowserResources {

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;
    private boolean tracing;

    public void open() {
        playwright = Playwright.create();
        browser = launchBrowser();
        context = browser.newContext();
        context.setDefaultTimeout(Config.timeoutMs());
        // Web-first assertions (assertThat(...)) use their own, separate default timeout
        // (5000ms) that context.setDefaultTimeout above does not affect — without this call
        // qa.timeoutMs would silently not apply to any pages/ assertion.
        PlaywrightAssertions.setDefaultAssertionTimeout(Config.timeoutMs());
        page = context.newPage();

        if (Config.artifactPolicy() != ArtifactPolicy.NEVER) {
            context.tracing().start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true));
            tracing = true;
        }
    }

    public Page page() {
        return page;
    }

    public void captureScreenshot(Path path) {
        createParentDirs(path);
        page.screenshot(new Page.ScreenshotOptions().setPath(path));
    }

    /** Stops tracing, saving to {@code path} only when {@code save} is true; otherwise discards it. */
    public void finishTracing(boolean save, Path path) {
        if (!tracing) {
            return;
        }
        if (save) {
            createParentDirs(path);
            context.tracing().stop(new Tracing.StopOptions().setPath(path));
        } else {
            context.tracing().stop();
        }
        tracing = false;
    }

    private static void createParentDirs(Path path) {
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void close() {
        if (context != null) {
            context.close();
        }
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    private Browser launchBrowser() {
        String browserName = Config.browser().toLowerCase(Locale.ROOT);
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(Config.headless());
        return switch (browserName) {
            case "chromium" -> playwright.chromium().launch(options);
            default -> throw new ConfigValidationException(
                    "Unsupported qa.browser '" + browserName + "'. Only 'chromium' is supported "
                            + "in this phase; firefox/webkit are future work (see docs/architecture.md).");
        };
    }
}
