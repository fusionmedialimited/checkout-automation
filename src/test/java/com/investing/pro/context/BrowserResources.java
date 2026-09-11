package com.investing.pro.context;

import com.investing.pro.config.ArtifactPolicy;
import com.investing.pro.config.Config;
import com.investing.pro.config.ConfigValidationException;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.Video;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;

/**
 * Owns one Playwright instance, browser, context and page for a single scenario. Injected by
 * PicoContainer so it is scenario-scoped: nothing here is static or shared across scenarios, and
 * every Playwright object stays on the thread that created it, per the framework's browser
 * lifecycle rules (see docs/architecture.md). Sequential execution only — this class is not
 * thread-safe by design.
 */
public final class BrowserResources {

    private static final Logger LOG = LoggerFactory.getLogger(BrowserResources.class);

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;
    private boolean tracing;
    private Path videoDir;

    public void open() {
        playwright = Playwright.create();
        browser = launchBrowser();

        Browser.NewContextOptions contextOptions = new Browser.NewContextOptions();
        if (Config.artifactPolicy() != ArtifactPolicy.NEVER) {
            // Recording must be requested at context-creation time, before this scenario's
            // outcome is known — same reasoning as tracing below. Playwright writes to an
            // auto-named file under this temp directory; finishVideo() moves or discards it
            // once the outcome (and this scenario's real artifact name) is known.
            videoDir = createTempDir();
            contextOptions.setRecordVideoDir(videoDir);
        }
        context = browser.newContext(contextOptions);
        context.setDefaultTimeout(Config.timeoutMs());
        // Web-first assertions (assertThat(...)) use their own, separate default timeout
        // (5000ms) that context.setDefaultTimeout above does not affect — without this call
        // qa.timeoutMs would silently not apply to any assertion made in the pages package.
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

    /**
     * Finalizes this scenario's video recording, saving it to {@code path} only when
     * {@code save} is true; otherwise the recording is discarded. Must be called after
     * {@link #closeContext()} but <b>before</b> {@link #close()} — Playwright only finishes
     * writing the video file once its context has closed, but saving/deleting it still needs a
     * live browser/driver connection, which {@link #close()} tears down.
     */
    public void finishVideo(boolean save, Path path) {
        if (videoDir == null || page == null) {
            return;
        }
        try {
            Video video = page.video();
            if (video != null) {
                if (save) {
                    createParentDirs(path);
                    video.saveAs(path);
                }
                video.delete();
            }
        } catch (PlaywrightException e) {
            LOG.warn("Failed to finalize video recording", e);
        } finally {
            deleteDirQuietly(videoDir);
            videoDir = null;
        }
    }

    private static void createParentDirs(Path path) {
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("qa-video-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Best-effort: this only cleans up scratch space, never worth failing the scenario over. */
    private static void deleteDirQuietly(Path dir) {
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort cleanup only
                }
            });
        } catch (IOException e) {
            LOG.warn("Failed to clean up temp video directory {}", dir, e);
        }
    }

    /**
     * Closes only the browser context, leaving the browser and Playwright process open. Split
     * out from {@link #close()} so a caller can finalize the scenario's video recording (see
     * {@link #finishVideo}) in between: that needs the context already closed, but still needs
     * the browser/driver connection {@link #close()} would otherwise tear down.
     */
    public void closeContext() {
        closeQuietly("context", context);
    }

    /**
     * Best-effort: closing one resource never skips the other, even if it throws. Call after
     * {@link #closeContext()} (and {@link #finishVideo}, if applicable) — calling this first
     * would leave no live connection for {@link #finishVideo} to save/delete the recording with.
     */
    public void close() {
        closeQuietly("browser", browser);
        closeQuietly("playwright", playwright);
    }

    private void closeQuietly(String resourceName, AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception e) {
            LOG.warn("Failed to close Playwright {}", resourceName, e);
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
