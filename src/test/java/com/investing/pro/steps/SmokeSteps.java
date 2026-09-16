package com.investing.pro.steps;

import com.investing.pro.config.Config;
import com.investing.pro.context.BrowserResources;
import com.investing.pro.pages.LandingPage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Step definitions for the QA smoke check. Must never create a user or submit a payment.
 * Locators and raw Playwright interaction stay in {@link LandingPage}; assertions belong here,
 * per BDD convention (see {@code BasePage}'s Javadoc) — this class asserts against what
 * {@link LandingPage} exposes, rather than delegating the assertion itself to the page object.
 */
public final class SmokeSteps {

    private final BrowserResources browserResources;
    private LandingPage landingPage;

    public SmokeSteps(BrowserResources browserResources) {
        this.browserResources = browserResources;
    }

    @Given("I open the InvestingPro landing page on the configured QA target")
    public void openLandingPage() {
        landingPage = new LandingPage(browserResources.page());
        landingPage.open(Config.baseUrl());
    }

    @Then("the page shows a stable InvestingPro indicator")
    public void verifyStableIndicator() {
        assertThat(landingPage.page()).hasTitle(landingPage.titlePattern());
        assertThat(landingPage.getStartedCta()).isVisible();
    }
}
