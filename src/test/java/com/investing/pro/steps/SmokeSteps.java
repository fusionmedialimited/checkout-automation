package com.investing.pro.steps;

import com.investing.pro.config.Config;
import com.investing.pro.context.BrowserResources;
import com.investing.pro.pages.LandingPage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

/**
 * Step definitions for the QA smoke check. Must never create a user or submit a payment.
 * Locators and Playwright assertions stay in {@link LandingPage}; this class only orchestrates.
 */
public final class SmokeSteps {

    private final BrowserResources browserResources;
    private LandingPage landingPage;

    public SmokeSteps(BrowserResources browserResources) {
        this.browserResources = browserResources;
    }

    @Given("I open the InvestingPro landing page on master QA")
    public void openLandingPage() {
        landingPage = new LandingPage(browserResources.page());
        landingPage.open(Config.baseUrl());
    }

    @Then("the page shows a stable InvestingPro indicator")
    public void verifyStableIndicator() {
        landingPage.assertLoaded();
    }
}
