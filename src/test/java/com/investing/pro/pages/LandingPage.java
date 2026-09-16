package com.investing.pro.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.regex.Pattern;

/**
 * The InvestingPro landing page. Only used by the smoke check today, so it only exposes what
 * that scenario needs.
 *
 * <p>Verified against master QA (master--www.ams-qa.finboxgcp.investing.com/pro/) via an actual
 * headless Playwright run on 2026-09-09: the app is a client-side-rendered SPA (a plain HTTP
 * fetch of the URL returns no usable body — DOM assertions here require a real browser), the
 * document title stably contains "InvestingPro" (the full title, e.g. "InvestingPro: Stock
 * Research &amp; Analysis Tool - Investing.com", is SEO copy and not asserted verbatim), and a
 * fresh, cookie-less visit lands on a marketing page — behind an unavoidable
 * "We Care About Your Privacy" consent overlay — whose header nav stably contains the text
 * "Get Started".
 *
 * <p>Two earlier assumptions, both made from a real Chrome session with existing
 * cookies/consent rather than the automated suite's fresh headless context, turned out wrong
 * once actually run: (1) {@code page.navigate} followed by
 * {@code waitForLoadState(NETWORKIDLE)} hangs forever because this app streams live price
 * updates continuously, so network never goes idle; (2) that session's nav showed "Upgrade to
 * Pro+" on a logged-in-style ticker dashboard view, which a fresh unauthenticated visit never
 * reaches — it lands on this marketing page instead. Both are why this class only exposes
 * elements confirmed present on an actual cold, cookie-less load. No other page in this project
 * has been inspected; treat any other selector as unvalidated until it is.
 *
 * <p>Deliberately does not wait for {@code networkidle}: this app streams live price updates
 * continuously (confirmed via an actual QA smoke run that timed out waiting for the network to
 * go idle), so that load-state event never fires. Navigation only waits for Playwright's default
 * {@code load} event; the step definition that asserts on {@link #titlePattern()} and
 * {@link #getStartedCta()} relies on Playwright's web-first assertions to retry until the
 * title/CTA actually appear — this class only identifies them, it doesn't assert on them (that
 * belongs in the step class, per BDD convention — see {@link BasePage}).
 */
public final class LandingPage extends BasePage {

    private static final Pattern TITLE_CONTAINS_INVESTINGPRO = Pattern.compile("InvestingPro");

    public LandingPage(Page page) {
        super(page);
    }

    public void open(String baseUrl) {
        page.navigate(baseUrl);
    }

    /** The stable title pattern described in the class Javadoc above, for a step to assert on. */
    public Pattern titlePattern() {
        return TITLE_CONTAINS_INVESTINGPRO;
    }

    public Locator getStartedCta() {
        return page.getByText("Get Started").first();
    }
}
